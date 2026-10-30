-- Migration: 202610260005_fix_import_csv_update.sql
-- Description: Fix CSV bulk import in UPDATE mode, remove legacy class_mappings requirement,
--              enable correct class transfer without duplicate enrollments or affecting historical attendance.
BEGIN;

-- 1. Ensure classes table allows nullable subject_name and lecturer_id (pure student roster group)
ALTER TABLE public.classes ALTER COLUMN subject_name DROP NOT NULL;
ALTER TABLE public.classes ALTER COLUMN lecturer_id DROP NOT NULL;

-- 2. Ensure attendance_sessions has subject_name and lecturer_id
ALTER TABLE public.attendance_sessions ADD COLUMN IF NOT EXISTS subject_name text;
ALTER TABLE public.attendance_sessions ADD COLUMN IF NOT EXISTS lecturer_id uuid REFERENCES public.profiles(id);

-- 3. Replace admin_import_students RPC with safe class transfer & 3-field format
DROP FUNCTION IF EXISTS public.admin_import_students(jsonb, text, uuid);
DROP FUNCTION IF EXISTS public.admin_import_students(jsonb, text);

CREATE OR REPLACE FUNCTION public.admin_import_students(
    payload jsonb,
    mode text DEFAULT 'SKIP'
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    v_item jsonb;
    v_code text;
    v_name text;
    v_group text;
    v_class_id uuid;
    v_student_id uuid;
    v_old_name text;
    n_total integer := 0;
    n_new integer := 0;
    n_updated integer := 0;
    n_groups integer := 0;
    n_memberships integer := 0;
    v_now bigint := public.now_ms();
BEGIN
    -- Strict admin authorization check
    IF NOT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND is_active = true AND role = 'ADMIN'
    ) THEN
        RAISE EXCEPTION 'FORBIDDEN: Chỉ Quản trị viên (ADMIN) mới có quyền nhập danh sách sinh viên.'
        USING errcode = '42501';
    END IF;

    -- Validate import mode
    IF mode IS NULL OR mode NOT IN ('SKIP', 'UPDATE', 'INSERT') THEN
        RAISE EXCEPTION 'INVALID_INPUT: Chế độ import không hợp lệ (chỉ chấp nhận SKIP, UPDATE hoặc INSERT).'
        USING errcode = '22023';
    END IF;

    -- Validate payload
    IF payload IS NULL OR jsonb_typeof(payload->'students') <> 'array' THEN
        RAISE EXCEPTION 'INVALID_INPUT: Mảng students là bắt buộc trong payload.'
        USING errcode = '22023';
    END IF;

    IF jsonb_array_length(payload->'students') = 0 THEN
        RETURN jsonb_build_object(
            'totalProcessed', 0,
            'newStudents', 0,
            'updatedStudents', 0,
            'newClasses', 0,
            'newEnrollments', 0
        );
    END IF;

    -- Advisory lock to prevent race conditions during bulk operations
    PERFORM pg_catalog.pg_advisory_xact_lock(24681012);

    FOR v_item IN SELECT value FROM jsonb_array_elements(payload->'students')
    LOOP
        v_code := btrim(coalesce(v_item->>'masv', ''));
        v_name := btrim(coalesce(v_item->>'ho_ten', ''));
        v_group := btrim(coalesce(v_item->>'lop', ''));

        IF length(v_code) = 0 OR length(v_name) = 0 OR length(v_group) = 0 THEN
            RAISE EXCEPTION 'INVALID_INPUT: MASV, HoTen, Lop không được để trống (dòng %: masv=%, ho_ten=%, lop=%).',
                n_total + 1, v_code, v_name, v_group
            USING errcode = '22023';
        END IF;

        n_total := n_total + 1;

        -- 1. Resolve or auto-create class (Class only manages students, no teacher/subject required)
        SELECT id INTO v_class_id FROM public.classes WHERE class_code = v_group;
        IF v_class_id IS NULL THEN
            INSERT INTO public.classes (class_code, class_name, subject_name, lecturer_id, semester, academic_year, created_at)
            VALUES (v_group, 'Lớp ' || v_group, NULL, NULL, '', '', v_now)
            ON CONFLICT (class_code) DO NOTHING;

            SELECT id INTO v_class_id FROM public.classes WHERE class_code = v_group;
            n_groups := n_groups + 1;
        END IF;

        -- 2. Resolve existing student by student_code (MASV)
        SELECT id, full_name INTO v_student_id, v_old_name
        FROM public.students
        WHERE student_code = v_code
        FOR UPDATE;

        IF v_student_id IS NULL THEN
            -- New student insertion
            INSERT INTO public.students (student_code, full_name, status, created_at)
            VALUES (v_code, v_name, 'ACTIVE', v_now)
            RETURNING id INTO v_student_id;
            n_new := n_new + 1;

            -- Enroll student into the class
            INSERT INTO public.enrollments (class_id, student_id, status, created_at)
            VALUES (v_class_id, v_student_id, 'ACTIVE', v_now)
            ON CONFLICT (class_id, student_id) DO UPDATE SET status = 'ACTIVE';
            n_memberships := n_memberships + 1;

        ELSIF mode = 'INSERT' THEN
            RAISE EXCEPTION 'DUPLICATE_STUDENT: Sinh viên có mã % đã tồn tại trong hệ thống.', v_code
            USING errcode = '23505';

        ELSIF mode = 'UPDATE' THEN
            -- Update student name if different
            IF v_old_name IS DISTINCT FROM v_name THEN
                UPDATE public.students SET full_name = v_name WHERE id = v_student_id;
                n_updated := n_updated + 1;
            END IF;

            -- Class transfer handling without affecting historical attendance:
            -- A. Deactivate active enrollments in any other classes
            UPDATE public.enrollments
            SET status = 'INACTIVE'
            WHERE student_id = v_student_id
              AND class_id <> v_class_id
              AND status = 'ACTIVE';

            -- B. Activate or insert enrollment in target class
            INSERT INTO public.enrollments (class_id, student_id, status, created_at)
            VALUES (v_class_id, v_student_id, 'ACTIVE', v_now)
            ON CONFLICT (class_id, student_id) DO UPDATE
            SET status = 'ACTIVE';

            n_memberships := n_memberships + 1;

        ELSIF mode = 'SKIP' THEN
            -- In SKIP mode: keep existing student record and class membership intact
            NULL;
        END IF;
    END LOOP;

    -- Record audit log
    INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, new_value, reason, created_at)
    VALUES (
        auth.uid(),
        'BULK_IMPORT_STUDENTS',
        'students',
        'batch-' || n_total,
        jsonb_build_object(
            'total', n_total,
            'new', n_new,
            'updated', n_updated,
            'classes', n_groups,
            'enrollments', n_memberships,
            'mode', mode
        )::text,
        'Import CSV sinh viên (' || mode || '): ' || n_new || ' mới, ' || n_updated || ' cập nhật, ' || n_groups || ' lớp mới',
        v_now
    );

    RETURN jsonb_build_object(
        'totalProcessed', n_total,
        'newStudents', n_new,
        'updatedStudents', n_updated,
        'newClasses', n_groups,
        'newEnrollments', n_memberships
    );
END;
$$;

-- 4. Single student save wrapper using the same robust 3-field logic
CREATE OR REPLACE FUNCTION public.admin_save_student_with_class(
    p_masv text,
    p_full_name text,
    p_class_code text,
    p_mode text DEFAULT 'UPDATE'
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
    RETURN public.admin_import_students(
        jsonb_build_object('students', jsonb_build_array(
            jsonb_build_object('masv', p_masv, 'ho_ten', p_full_name, 'lop', p_class_code)
        )),
        p_mode
    );
END;
$$;

-- 5. Revoke / Grant permissions
REVOKE ALL ON FUNCTION public.admin_import_students(jsonb, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_import_students(jsonb, text) TO authenticated;

REVOKE ALL ON FUNCTION public.admin_save_student_with_class(text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_save_student_with_class(text, text, text, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;
