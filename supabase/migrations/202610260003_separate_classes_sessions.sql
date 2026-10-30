-- Smart Attendance migration 003: groups/classes independent of sessions and lecturers.
-- PREREQUISITES: 001 applied, 002 optional. Apply with updated Android client; backup first.
-- Do not execute 001 again. All existing students/enrollments/records remain unchanged.
BEGIN;

-- 1. A class is a student group. Legacy teaching fields stay for historical compatibility,
--    but may be NULL and must not be used by new Android screens.
ALTER TABLE public.classes
    ALTER COLUMN subject_name DROP NOT NULL,
    ALTER COLUMN lecturer_id DROP NOT NULL,
    ALTER COLUMN semester SET DEFAULT '',
    ALTER COLUMN academic_year SET DEFAULT '';

-- 2. Teacher and subject belong to each attendance SESSION, not the class.
ALTER TABLE public.attendance_sessions
    ADD COLUMN IF NOT EXISTS subject_name text,
    ADD COLUMN IF NOT EXISTS lecturer_id uuid REFERENCES public.profiles(id) ON DELETE RESTRICT;

-- Backfill historical sessions, retaining historical lecturer and subject.
UPDATE public.attendance_sessions s
SET subject_name = COALESCE(s.subject_name, c.subject_name, 'Môn học lịch sử'),
    lecturer_id = COALESCE(s.lecturer_id, c.lecturer_id, s.created_by)
FROM public.classes c
WHERE s.class_id = c.id
  AND (s.subject_name IS NULL OR s.lecturer_id IS NULL);

ALTER TABLE public.attendance_sessions
    ALTER COLUMN subject_name SET NOT NULL,
    ALTER COLUMN lecturer_id SET NOT NULL;

ALTER TABLE public.attendance_sessions
    ADD CONSTRAINT session_subject_nonblank CHECK (length(btrim(subject_name)) > 0);
CREATE INDEX IF NOT EXISTS attendance_sessions_lecturer_idx
    ON public.attendance_sessions(lecturer_id, start_time DESC);

-- 3. Permission helpers, based on the ASSIGNED SESSION teacher.
CREATE OR REPLACE FUNCTION public.is_active_lecturer() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (SELECT 1 FROM public.profiles p
                 WHERE p.id = auth.uid() AND p.is_active AND p.role = 'LECTURER');
$$;

CREATE OR REPLACE FUNCTION public.can_manage_session(p_session_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT public.is_admin() OR EXISTS (
    SELECT 1 FROM public.attendance_sessions s
    JOIN public.profiles p ON p.id = auth.uid()
    WHERE s.id = p_session_id AND s.lecturer_id = p.id
      AND p.is_active AND p.role = 'LECTURER'
  );
$$;

-- Keep this old helper for roster/enrollment visibility and compatible callers.
-- It no longer uses classes.lecturer_id.
CREATE OR REPLACE FUNCTION public.can_manage(p_class_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT public.is_admin() OR EXISTS (
    SELECT 1 FROM public.attendance_sessions s
    JOIN public.profiles p ON p.id = auth.uid()
    WHERE s.class_id = p_class_id AND s.lecturer_id = p.id
      AND p.is_active AND p.role = 'LECTURER'
  );
$$;

CREATE OR REPLACE FUNCTION public.can_view_student(p_student_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT public.is_admin() OR EXISTS (
    SELECT 1 FROM public.session_roster r
    JOIN public.attendance_sessions s ON s.id = r.session_id
    JOIN public.profiles p ON p.id = auth.uid()
    WHERE r.student_id = p_student_id AND s.lecturer_id = p.id
      AND p.is_active AND p.role = 'LECTURER'
  );
$$;

-- Everyone teaching may list group codes to plan their own session; details are scoped.
DROP POLICY IF EXISTS class_read ON public.classes;
CREATE POLICY class_read ON public.classes FOR SELECT TO authenticated
USING (public.is_admin() OR public.is_active_lecturer());
DROP POLICY IF EXISTS session_read ON public.attendance_sessions;
CREATE POLICY session_read ON public.attendance_sessions FOR SELECT TO authenticated
USING (public.can_manage_session(id));
DROP POLICY IF EXISTS roster_read ON public.session_roster;
CREATE POLICY roster_read ON public.session_roster FOR SELECT TO authenticated
USING (public.can_manage_session(session_id));
DROP POLICY IF EXISTS record_read ON public.attendance_records;
CREATE POLICY record_read ON public.attendance_records FOR SELECT TO authenticated
USING (public.can_manage_session(session_id));

-- Admin and active teachers may query the names of active lecturer accounts.
-- Do not expose email, password information or inactive profiles.
CREATE OR REPLACE FUNCTION public.list_active_lecturers() RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT (public.is_admin() OR public.is_active_lecturer()) THEN
    RAISE EXCEPTION 'FORBIDDEN' USING errcode='42501';
  END IF;
  RETURN COALESCE((
    SELECT jsonb_agg(jsonb_build_object('id',p.id,'full_name',p.full_name)
                     ORDER BY p.full_name, p.id)
    FROM public.profiles p WHERE p.role='LECTURER' AND p.is_active
  ), '[]'::jsonb);
END;
$$;

-- 4. Create a SESSION with explicit teacher + subject; copy group roster at creation.
CREATE OR REPLACE FUNCTION public.create_attendance_session(payload jsonb) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE s public.attendance_sessions;
BEGIN
  IF payload IS NULL OR jsonb_typeof(payload) <> 'object' THEN
    RAISE EXCEPTION 'INVALID_INPUT';
  END IF;
  s := jsonb_populate_record(NULL::public.attendance_sessions, payload);
  IF s.id IS NULL OR s.class_id IS NULL OR s.lecturer_id IS NULL
     OR NULLIF(btrim(COALESCE(s.subject_name,'')), '') IS NULL
     OR NULLIF(btrim(COALESCE(s.session_name,'')), '') IS NULL
     OR s.start_time IS NULL OR s.late_after IS NULL OR s.end_time IS NULL
     OR s.session_date IS NULL OR s.status IS NULL
     OR s.status NOT IN ('SCHEDULED','OPEN')
     OR s.start_time > s.late_after OR s.late_after >= s.end_time
     OR s.end_time <= public.now_ms() THEN
    RAISE EXCEPTION 'INVALID_INPUT: session fields';
  END IF;
  PERFORM s.session_date::date;
  PERFORM 1 FROM public.classes WHERE id = s.class_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'INVALID_INPUT: class not found'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.profiles p
                 WHERE p.id=s.lecturer_id AND p.role='LECTURER' AND p.is_active) THEN
    RAISE EXCEPTION 'INVALID_INPUT: active lecturer required';
  END IF;
  IF NOT (public.is_admin() OR (public.is_active_lecturer() AND s.lecturer_id=auth.uid())) THEN
    RAISE EXCEPTION 'FORBIDDEN' USING errcode='42501';
  END IF;
  s.created_by := auth.uid();
  s.created_at := public.now_ms();
  INSERT INTO public.attendance_sessions
      (id,class_id,session_name,session_date,start_time,late_after,end_time,
       status,created_by,created_at,subject_name,lecturer_id)
  VALUES (s.id,s.class_id,s.session_name,s.session_date,s.start_time,
          s.late_after,s.end_time,s.status,s.created_by,s.created_at,
          btrim(s.subject_name),s.lecturer_id);
  INSERT INTO public.session_roster(session_id,student_id)
  SELECT s.id,e.student_id FROM public.enrollments e
  JOIN public.students st ON st.id=e.student_id
  WHERE e.class_id=s.class_id AND e.status='ACTIVE' AND st.status='ACTIVE';
  INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,new_value)
  VALUES (auth.uid(),'CREATE_SESSION','attendance_sessions',s.id::text,to_jsonb(s)::text);
END;
$$;

-- Mutations must check permission for this session, never just its class.
CREATE OR REPLACE FUNCTION public.set_session_status(p_session_id uuid,p_status text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE s public.attendance_sessions;
BEGIN
  SELECT * INTO s FROM public.attendance_sessions WHERE id=p_session_id FOR UPDATE;
  IF NOT FOUND OR NOT public.can_manage_session(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN' USING errcode='42501';
  END IF;
  IF p_status IS NULL OR p_status NOT IN ('OPEN','CLOSED') OR s.status='CLOSED'
     OR (p_status='OPEN' AND public.now_ms()>=s.end_time) THEN
    RAISE EXCEPTION 'SESSION_CLOSED';
  END IF;
  UPDATE public.attendance_sessions SET status=p_status WHERE id=s.id;
  INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,old_value,new_value)
  VALUES (auth.uid(),'SESSION_STATUS','attendance_sessions',s.id::text,
          to_jsonb(s)::text,
          (SELECT to_jsonb(t)::text FROM public.attendance_sessions t WHERE t.id=s.id));
END;
$$;

CREATE OR REPLACE FUNCTION public.record_attendance(p_session_id uuid,p_card_code text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE s public.attendance_sessions; c public.student_cards;
        st public.students; r public.attendance_records; t bigint;
BEGIN
  SELECT * INTO s FROM public.attendance_sessions WHERE id=p_session_id FOR UPDATE;
  IF NOT FOUND OR NOT public.can_manage_session(p_session_id) THEN
    RETURN jsonb_build_object('code','FORBIDDEN');
  END IF;
  t := public.now_ms();
  IF s.status <> 'OPEN' OR t < s.start_time OR t >= s.end_time THEN
    RETURN jsonb_build_object('code','SESSION_CLOSED');
  END IF;
  IF p_card_code IS NULL OR length(p_card_code)>512 THEN
    RETURN jsonb_build_object('code','INVALID_CARD');
  END IF;
  SELECT * INTO c FROM public.student_cards
  WHERE card_code=p_card_code AND is_active FOR SHARE;
  IF NOT FOUND THEN RETURN jsonb_build_object('code','INVALID_CARD'); END IF;
  SELECT * INTO st FROM public.students WHERE id=c.student_id FOR SHARE;
  IF st.status <> 'ACTIVE' THEN
    RETURN jsonb_build_object('code','INACTIVE_STUDENT');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.session_roster
                 WHERE session_id=s.id AND student_id=st.id) THEN
    RETURN jsonb_build_object('code','WRONG_CLASS');
  END IF;
  SELECT * INTO r FROM public.attendance_records
  WHERE session_id=s.id AND student_id=st.id;
  IF FOUND THEN
    RETURN jsonb_build_object('code','DUPLICATE','student',to_jsonb(st),'record',to_jsonb(r));
  END IF;
  INSERT INTO public.attendance_records(session_id,student_id,status,scanned_at,recorded_by,method)
  VALUES (s.id,st.id,CASE WHEN t>=s.late_after THEN 'LATE' ELSE 'PRESENT' END,
          t,auth.uid(),'SCAN') RETURNING * INTO r;
  RETURN jsonb_build_object('code',r.status,'student',to_jsonb(st),'record',to_jsonb(r));
END;
$$;

CREATE OR REPLACE FUNCTION public.adjust_attendance(
  p_session_id uuid,p_student_id uuid,p_status text,p_reason text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE s public.attendance_sessions;
        old_record public.attendance_records; new_record public.attendance_records;
BEGIN
  SELECT * INTO s FROM public.attendance_sessions WHERE id=p_session_id FOR UPDATE;
  IF NOT FOUND OR NOT public.can_manage_session(p_session_id) THEN
    RAISE EXCEPTION 'FORBIDDEN' USING errcode='42501';
  END IF;
  IF p_reason IS NULL OR length(btrim(p_reason)) < 3
     OR p_status IS NULL OR p_status NOT IN ('PRESENT','LATE','EXCUSED','ABSENT')
     OR NOT EXISTS (SELECT 1 FROM public.session_roster
                    WHERE session_id=s.id AND student_id=p_student_id) THEN
    RAISE EXCEPTION 'INVALID_INPUT';
  END IF;
  SELECT * INTO old_record FROM public.attendance_records
  WHERE session_id=s.id AND student_id=p_student_id;
  IF p_status='ABSENT' THEN
    DELETE FROM public.attendance_records
    WHERE session_id=s.id AND student_id=p_student_id;
  ELSE
    INSERT INTO public.attendance_records
      (session_id,student_id,status,scanned_at,recorded_by,method,note)
    VALUES (s.id,p_student_id,p_status,public.now_ms(),auth.uid(),'MANUAL',btrim(p_reason))
    ON CONFLICT(session_id,student_id) DO UPDATE
       SET status=excluded.status,recorded_by=excluded.recorded_by,
           method='MANUAL',note=excluded.note RETURNING * INTO new_record;
  END IF;
  INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,old_value,new_value,reason)
  VALUES (auth.uid(),'ADJUST_ATTENDANCE','attendance_records',
          s.id::text||'/'||p_student_id::text,
          coalesce(to_jsonb(old_record)::text,''),
          coalesce(to_jsonb(new_record)::text,''),btrim(p_reason));
END;
$$;

-- 5. Admin save: only student code/name and group metadata are required.
-- Other legacy entities remain supported without dropping their existing columns.
CREATE OR REPLACE FUNCTION public.admin_save(p_entity text,payload jsonb) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE st public.students; ca public.student_cards; en public.enrollments;
        pr public.profiles; old_data text := ''; new_data text; entity_id uuid;
BEGIN
  IF NOT public.is_admin() THEN RAISE EXCEPTION 'FORBIDDEN' USING errcode='42501'; END IF;
  entity_id := (payload->>'id')::uuid;
  IF entity_id IS NULL THEN RAISE EXCEPTION 'INVALID_INPUT: id required'; END IF;
  CASE p_entity
  WHEN 'students' THEN
    SELECT to_jsonb(t)::text INTO old_data FROM public.students t
    WHERE id=entity_id FOR UPDATE;
    INSERT INTO public.students(id,student_code,full_name,status)
    VALUES (entity_id, NULLIF(btrim(payload->>'student_code'),''),
            NULLIF(btrim(payload->>'full_name'),''),
            COALESCE(NULLIF(payload->>'status',''),'ACTIVE'))
    ON CONFLICT(id) DO UPDATE SET
      student_code=excluded.student_code, full_name=excluded.full_name,
      status=COALESCE(NULLIF(payload->>'status',''),students.status);
    SELECT to_jsonb(t)::text INTO new_data FROM public.students t WHERE id=entity_id;
  WHEN 'classes' THEN
    SELECT to_jsonb(t)::text INTO old_data FROM public.classes t
    WHERE id=entity_id FOR UPDATE;
    INSERT INTO public.classes(id,class_code,class_name,subject_name,lecturer_id,semester,academic_year)
    VALUES (entity_id,NULLIF(btrim(payload->>'class_code'),''),
            COALESCE(NULLIF(btrim(payload->>'class_name'),''),
                     NULLIF(btrim(payload->>'class_code'),'')),
            NULL,NULL,'','')
    ON CONFLICT(id) DO UPDATE SET
      class_code=excluded.class_code, class_name=excluded.class_name;
    SELECT to_jsonb(t)::text INTO new_data FROM public.classes t WHERE id=entity_id;
  WHEN 'student_cards' THEN
    ca:=jsonb_populate_record(NULL::public.student_cards,payload);
    ca.created_at:=public.now_ms();
    SELECT to_jsonb(t)::text INTO old_data FROM public.student_cards t
    WHERE id=entity_id FOR UPDATE;
    INSERT INTO public.student_cards SELECT ca.*
    ON CONFLICT(id) DO UPDATE SET student_id=excluded.student_id,
       card_code=excluded.card_code,barcode_format=excluded.barcode_format,
       is_active=excluded.is_active;
    SELECT to_jsonb(t)::text INTO new_data FROM public.student_cards t
    WHERE id=entity_id;
  WHEN 'enrollments' THEN
    en:=jsonb_populate_record(NULL::public.enrollments,payload);
    en.created_at:=public.now_ms();
    PERFORM 1 FROM public.classes WHERE id=en.class_id FOR UPDATE;
    SELECT to_jsonb(t)::text INTO old_data FROM public.enrollments t
    WHERE id=entity_id FOR UPDATE;
    INSERT INTO public.enrollments SELECT en.*
    ON CONFLICT(id) DO UPDATE SET class_id=excluded.class_id,
      student_id=excluded.student_id,status=excluded.status;
    SELECT to_jsonb(t)::text INTO new_data FROM public.enrollments t
    WHERE id=entity_id;
  WHEN 'profiles' THEN
    pr:=jsonb_populate_record(NULL::public.profiles,payload);
    IF entity_id=auth.uid() AND (pr.role<>'ADMIN' OR NOT pr.is_active) THEN
      RAISE EXCEPTION 'INVALID_INPUT';
    END IF;
    SELECT to_jsonb(t)::text INTO old_data FROM public.profiles t
    WHERE id=entity_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'INVALID_INPUT: create Auth account first'; END IF;
    UPDATE public.profiles SET full_name=pr.full_name,role=pr.role,
       is_active=pr.is_active WHERE id=entity_id;
    SELECT to_jsonb(t)::text INTO new_data FROM public.profiles t WHERE id=entity_id;
  ELSE
    RAISE EXCEPTION 'INVALID_INPUT';
  END CASE;
  INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,old_value,new_value,reason)
  VALUES (auth.uid(),'ADMIN_SAVE',p_entity,entity_id::text,
          coalesce(old_data,''),new_data,'Quản lý dữ liệu');
END;
$$;

-- 6. Bulk import: MASV + HoTen + Lop. No subjects, no teacher mapping, no QR assumptions.
-- Compatible with the 002 two-argument RPC; remove old three-argument overload if present.
DROP FUNCTION IF EXISTS public.admin_import_students(jsonb,text,uuid);
CREATE OR REPLACE FUNCTION public.admin_import_students(payload jsonb,mode text DEFAULT 'SKIP') RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE v_item jsonb; v_code text; v_name text; v_group text;
        v_class_id uuid; v_student_id uuid; v_old_name text;
        n_total integer:=0; n_new integer:=0; n_updated integer:=0;
        n_groups integer:=0; n_memberships integer:=0;
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'FORBIDDEN' USING errcode='42501';
  END IF;
  IF mode IS NULL OR mode NOT IN ('SKIP','UPDATE','INSERT') THEN
    RAISE EXCEPTION 'INVALID_INPUT: import mode';
  END IF;
  IF payload IS NULL OR jsonb_typeof(payload->'students') <> 'array' THEN
    RAISE EXCEPTION 'INVALID_INPUT: students array required';
  END IF;
  IF jsonb_array_length(payload->'students') = 0 THEN
    RETURN jsonb_build_object('totalProcessed',0,'newStudents',0,'updatedStudents',0,
                              'newClasses',0,'newEnrollments',0);
  END IF;
  -- Lock parallel imports to avoid duplicate group construction under concurrency.
  PERFORM pg_catalog.pg_advisory_xact_lock(24681012);
  FOR v_item IN SELECT value FROM jsonb_array_elements(payload->'students') LOOP
    v_code := btrim(coalesce(v_item->>'masv',''));
    v_name := btrim(coalesce(v_item->>'ho_ten',''));
    v_group := btrim(coalesce(v_item->>'lop',''));
    IF v_code !~ '^[A-Za-z0-9_-]{3,40}$'
       OR v_name = '' OR v_group = '' THEN
      RAISE EXCEPTION 'INVALID_INPUT: MASV, HoTen, Lop required; row %', n_total + 1;
    END IF;
    n_total := n_total + 1;
    SELECT id INTO v_class_id FROM public.classes WHERE class_code=v_group;
    IF v_class_id IS NULL THEN
      INSERT INTO public.classes(class_code,class_name,subject_name,lecturer_id,semester,academic_year)
      VALUES (v_group,v_group,NULL,NULL,'','') ON CONFLICT(class_code) DO NOTHING;
      SELECT id INTO v_class_id FROM public.classes WHERE class_code=v_group;
      n_groups := n_groups+1;
    END IF;
    SELECT id,full_name INTO v_student_id,v_old_name
    FROM public.students WHERE student_code=v_code FOR UPDATE;
    IF v_student_id IS NULL THEN
      INSERT INTO public.students(student_code,full_name)
      VALUES (v_code,v_name) RETURNING id INTO v_student_id;
      n_new := n_new+1;
    ELSIF mode='INSERT' THEN
      RAISE EXCEPTION 'DUPLICATE_STUDENT: %',v_code;
    ELSIF mode='UPDATE' AND v_old_name IS DISTINCT FROM v_name THEN
      UPDATE public.students SET full_name=v_name WHERE id=v_student_id;
      n_updated := n_updated+1;
    END IF;
    INSERT INTO public.enrollments(class_id,student_id,status)
    VALUES(v_class_id,v_student_id,'ACTIVE')
    ON CONFLICT(class_id,student_id) DO NOTHING;
    IF FOUND THEN
      n_memberships := n_memberships+1;
    ELSE
      UPDATE public.enrollments SET status='ACTIVE'
      WHERE class_id=v_class_id AND student_id=v_student_id AND status='INACTIVE';
    END IF;
  END LOOP;
  INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,new_value,reason)
  VALUES(auth.uid(),'BULK_IMPORT_STUDENTS','students','batch',
         jsonb_build_object('total',n_total,'new',n_new,'updated',n_updated,
                            'classes',n_groups,'enrollments',n_memberships)::text,
         'Import CSV: MASV, HoTen, Lop');
  RETURN jsonb_build_object('totalProcessed',n_total,'newStudents',n_new,
                            'updatedStudents',n_updated,'newClasses',n_groups,
                            'newEnrollments',n_memberships);
END;
$$;

-- Single-form add: exact same 3 fields as CSV. No lecturer, subject or class mapping.
CREATE OR REPLACE FUNCTION public.admin_save_student_with_class(
  p_masv text,p_full_name text,p_class_code text,p_mode text DEFAULT 'UPDATE') RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  RETURN public.admin_import_students(
    jsonb_build_object('students',jsonb_build_array(
      jsonb_build_object('masv',p_masv,'ho_ten',p_full_name,'lop',p_class_code))),p_mode);
END;
$$;

REVOKE ALL ON FUNCTION public.is_active_lecturer(),public.can_manage_session(uuid),
 public.list_active_lecturers(),public.admin_save_student_with_class(text,text,text,text)
 FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.is_active_lecturer(),public.can_manage_session(uuid),
 public.list_active_lecturers(),public.admin_save_student_with_class(text,text,text,text)
 TO authenticated;
REVOKE ALL ON FUNCTION public.admin_import_students(jsonb,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_import_students(jsonb,text) TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;
