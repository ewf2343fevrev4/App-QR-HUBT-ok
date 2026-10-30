-- ============================================================================
-- Migration 006: Fix Online Login Deserialization & Raw Student MASV QR Scan
-- ============================================================================
-- 1. App Snapshot: Safely coalesces nullable session fields (subject_name,
--    lecturer_id) so historical records never emit bare null literals.
-- 2. Record Attendance:
--    - Supports Raw numeric MASV (e.g. '2823156783') without any prefix.
--    - Supports Demo 'STUDENT_' prefix and registered physical barcodes.
--    - Granular timing validation:
--      * CLOSED -> SESSION_CLOSED
--      * status != OPEN -> SESSION_NOT_OPEN
--      * now < start_time -> NOT_STARTED ("Chưa đến giờ điểm danh")
--      * now >= end_time -> SESSION_EXPIRED ("Đã hết giờ điểm danh")
--    - Enforces RLS, session roster verification, and duplicate prevention.
-- ============================================================================

BEGIN;

-- 1. Safe Snapshot with fallback for historical records
CREATE OR REPLACE FUNCTION public.app_snapshot() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND is_active) THEN
    RAISE EXCEPTION 'INACTIVE_ACCOUNT' USING errcode = '42501';
  END IF;

  RETURN jsonb_build_object(
    'profiles', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.profiles t), '[]'),
    'students', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.students t), '[]'),
    'cards', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.student_cards t), '[]'),
    'classes', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.classes t), '[]'),
    'enrollments', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.enrollments t), '[]'),
    'sessions', COALESCE((
      SELECT jsonb_agg(
        to_jsonb(t) || jsonb_build_object(
          'subject_name', COALESCE(NULLIF(btrim(t.subject_name), ''), 'Chưa cập nhật môn học'),
          'lecturer_id', COALESCE(t.lecturer_id::text, '')
        )
      )
      FROM public.attendance_sessions t
    ), '[]'),
    'roster', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.session_roster t), '[]'),
    'records', COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM public.attendance_records t), '[]'),
    'logs', COALESCE((
      SELECT jsonb_agg(to_jsonb(t))
      FROM (SELECT * FROM public.audit_logs ORDER BY created_at DESC LIMIT 200) t
    ), '[]')
  );
END;
$$;

-- 2. Enhanced record_attendance with multi-tier student resolution & granular timing
CREATE OR REPLACE FUNCTION public.record_attendance(p_session_id uuid, p_card_code text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  s public.attendance_sessions;
  c public.student_cards;
  st public.students;
  r public.attendance_records;
  t bigint;
  v_scan text;
BEGIN
  SELECT * INTO s FROM public.attendance_sessions WHERE id = p_session_id FOR UPDATE;
  IF NOT FOUND OR NOT public.can_manage_session(p_session_id) THEN
    RETURN jsonb_build_object('code', 'FORBIDDEN');
  END IF;

  t := public.now_ms();

  -- Granular session timing and lifecycle state
  IF s.status = 'CLOSED' THEN
    RETURN jsonb_build_object('code', 'SESSION_CLOSED');
  END IF;
  IF s.status <> 'OPEN' THEN
    RETURN jsonb_build_object('code', 'SESSION_NOT_OPEN');
  END IF;
  IF t < s.start_time THEN
    RETURN jsonb_build_object('code', 'NOT_STARTED');
  END IF;
  IF t >= s.end_time THEN
    RETURN jsonb_build_object('code', 'SESSION_EXPIRED');
  END IF;

  v_scan := btrim(p_card_code);
  IF v_scan IS NULL OR v_scan = '' OR length(v_scan) > 512 THEN
    RETURN jsonb_build_object('code', 'INVALID_CARD');
  END IF;

  -- Check if an issued card has been explicitly deactivated/revoked
  SELECT * INTO c FROM public.student_cards WHERE card_code = v_scan FOR SHARE;
  IF FOUND AND NOT c.is_active THEN
    RETURN jsonb_build_object('code', 'INVALID_CARD');
  END IF;
  IF NOT FOUND AND NOT v_scan LIKE 'STUDENT_%' THEN
    SELECT * INTO c FROM public.student_cards WHERE card_code = ('STUDENT_' || v_scan) FOR SHARE;
    IF FOUND AND NOT c.is_active THEN
      RETURN jsonb_build_object('code', 'INVALID_CARD');
    END IF;
  END IF;

  -- Multi-strategy Student Resolution:
  -- Tier 1: Direct student_code match (Raw MASV on student card, e.g. '2823156783')
  SELECT * INTO st FROM public.students WHERE student_code = v_scan FOR SHARE;

  -- Tier 2: Strip demo 'STUDENT_' prefix if present
  IF NOT FOUND AND v_scan LIKE 'STUDENT_%' THEN
    SELECT * INTO st FROM public.students WHERE student_code = substring(v_scan FROM 9) FOR SHARE;
  END IF;

  -- Tier 3: Registered card / barcode in student_cards
  IF NOT FOUND THEN
    SELECT * INTO c FROM public.student_cards
    WHERE card_code = v_scan AND is_active FOR SHARE;
    IF FOUND THEN
      SELECT * INTO st FROM public.students WHERE id = c.student_id FOR SHARE;
    END IF;
  END IF;

  -- Tier 4: Registered card in student_cards matching 'STUDENT_' || v_scan
  IF st.id IS NULL THEN
    SELECT * INTO c FROM public.student_cards
    WHERE card_code = ('STUDENT_' || v_scan) AND is_active FOR SHARE;
    IF FOUND THEN
      SELECT * INTO st FROM public.students WHERE id = c.student_id FOR SHARE;
    END IF;
  END IF;

  -- If student cannot be located by any strategy
  IF st.id IS NULL THEN
    RETURN jsonb_build_object('code', 'INVALID_CARD');
  END IF;

  -- Inactive student status check
  IF st.status <> 'ACTIVE' THEN
    RETURN jsonb_build_object('code', 'INACTIVE_STUDENT');
  END IF;

  -- Session roster enrollment check
  IF NOT EXISTS (
    SELECT 1 FROM public.session_roster
    WHERE session_id = s.id AND student_id = st.id
  ) THEN
    RETURN jsonb_build_object('code', 'WRONG_CLASS');
  END IF;

  -- Duplicate scan check
  SELECT * INTO r FROM public.attendance_records
  WHERE session_id = s.id AND student_id = st.id;
  IF FOUND THEN
    RETURN jsonb_build_object('code', 'DUPLICATE', 'student', to_jsonb(st), 'record', to_jsonb(r));
  END IF;

  -- Insert new attendance record
  INSERT INTO public.attendance_records(session_id, student_id, status, scanned_at, recorded_by, method)
  VALUES (
    s.id,
    st.id,
    CASE WHEN t >= s.late_after THEN 'LATE' ELSE 'PRESENT' END,
    t,
    auth.uid(),
    'SCAN'
  ) RETURNING * INTO r;

  RETURN jsonb_build_object('code', r.status, 'student', to_jsonb(st), 'record', to_jsonb(r));
END;
$$;

-- Ensure execution permissions
REVOKE ALL ON FUNCTION public.app_snapshot() FROM public, anon;
REVOKE ALL ON FUNCTION public.record_attendance(uuid, text) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.now_ms() TO authenticated;
GRANT EXECUTE ON FUNCTION public.app_snapshot() TO authenticated;
GRANT EXECUTE ON FUNCTION public.record_attendance(uuid, text) TO authenticated;

COMMIT;
