-- Migration: 202610260002_import_students.sql
-- Bulk import students safely by ADMIN with explicit class & lecturer mapping

create or replace function public.admin_import_students(
    payload jsonb,
    mode text default 'SKIP'
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor_id uuid;
    v_actor_role text;
    v_actor_active boolean;
    v_now bigint := public.now_ms();
    v_students jsonb;
    v_mappings jsonb;
    v_map_item jsonb;
    v_map_code text;
    v_map_target_id uuid;
    v_map_cname text;
    v_map_subject text;
    v_map_lec_id uuid;
    v_map_sem text;
    v_map_year text;
    v_lec_role text;
    v_lec_active boolean;
    v_item jsonb;
    v_masv text;
    v_hoten text;
    v_lop text;
    v_class_id uuid;
    v_student_id uuid;
    v_existing_student_id uuid;
    v_existing_name text;
    v_new_students int := 0;
    v_updated_students int := 0;
    v_new_classes int := 0;
    v_new_enrollments int := 0;
    v_total int := 0;
begin
    -- 1. Check admin permission
    v_actor_id := auth.uid();
    if v_actor_id is null then
        raise exception 'FORBIDDEN: Authentication required.';
    end if;

    select role, is_active into v_actor_role, v_actor_active
    from public.profiles where id = v_actor_id;

    if v_actor_role != 'ADMIN' or not coalesce(v_actor_active, false) then
        raise exception 'FORBIDDEN: Chỉ Quản trị viên (ADMIN) mới có quyền nhập danh sách sinh viên hàng loạt.';
    end if;

    if mode not in ('SKIP', 'UPDATE', 'INSERT') then
        raise exception 'INVALID_INPUT: Chế độ xử lý trùng không hợp lệ (phải là SKIP, UPDATE hoặc INSERT).';
    end if;

    -- Temporary table to hold class mappings for this batch
    create temp table if not exists _batch_class_mappings (
        class_code text primary key,
        class_id uuid not null
    ) on commit drop;
    truncate table _batch_class_mappings;

    -- 2. Process class mappings provided by Admin
    v_mappings := payload->'class_mappings';
    if v_mappings is not null and jsonb_typeof(v_mappings) = 'array' then
        for v_map_item in select * from jsonb_array_elements(v_mappings)
        loop
            v_map_code := trim(coalesce(v_map_item->>'class_code', ''));
            if length(v_map_code) = 0 then
                continue;
            end if;

            v_map_target_id := null;
            if v_map_item->>'target_class_id' is not null and length(trim(v_map_item->>'target_class_id')) > 0 then
                v_map_target_id := (v_map_item->>'target_class_id')::uuid;
            end if;

            if v_map_target_id is not null then
                -- Map to an existing class
                select id into v_class_id from public.classes where id = v_map_target_id;
                if v_class_id is null then
                    raise exception 'INVALID_INPUT: Lớp chỉ định (ID: %) không tồn tại.', v_map_target_id;
                end if;
            else
                -- Check if class already exists by class_code
                select id into v_class_id from public.classes where class_code = v_map_code;
                if v_class_id is null then
                    -- Creating a new class: MUST have explicit subject_name and lecturer_id
                    v_map_subject := trim(coalesce(v_map_item->>'subject_name', ''));
                    if length(v_map_subject) = 0 then
                        raise exception 'INVALID_INPUT: Lớp % chưa có môn học được phân công. Không được để trống môn học.', v_map_code;
                    end if;

                    if v_map_item->>'lecturer_id' is null or length(trim(v_map_item->>'lecturer_id')) = 0 then
                        raise exception 'INVALID_INPUT: Lớp % chưa có giảng viên phụ trách. Không tự gán ADMIN làm giảng viên.', v_map_code;
                    end if;
                    v_map_lec_id := (v_map_item->>'lecturer_id')::uuid;

                    -- Validate lecturer profile: MUST be active and MUST have role 'LECTURER' (not ADMIN)
                    select role, is_active into v_lec_role, v_lec_active
                    from public.profiles where id = v_map_lec_id;

                    if v_lec_role is null or not coalesce(v_lec_active, false) then
                        raise exception 'INVALID_INPUT: Giảng viên phụ trách cho lớp % không tồn tại hoặc chưa kích hoạt.', v_map_code;
                    end if;

                    if v_lec_role != 'LECTURER' then
                        raise exception 'INVALID_INPUT: Giảng viên phụ trách lớp % phải có vai trò LECTURER (không được phân công ADMIN làm giảng viên).', v_map_code;
                    end if;

                    v_map_cname := trim(coalesce(v_map_item->>'class_name', 'Lớp ' || v_map_code));
                    v_map_sem := coalesce(v_map_item->>'semester', '1');
                    v_map_year := coalesce(v_map_item->>'academic_year', '2026–2027');

                    v_class_id := gen_random_uuid();
                    insert into public.classes (id, class_code, class_name, subject_name, lecturer_id, semester, academic_year, created_at)
                    values (v_class_id, v_map_code, v_map_cname, v_map_subject, v_map_lec_id, v_map_sem, v_map_year, v_now);

                    v_new_classes := v_new_classes + 1;
                end if;
            end if;

            insert into _batch_class_mappings (class_code, class_id)
            values (v_map_code, v_class_id)
            on conflict (class_code) do update set class_id = excluded.class_id;
        end loop;
    end if;

    -- 3. Process students
    v_students := payload->'students';
    if v_students is null or jsonb_array_length(v_students) = 0 then
        return jsonb_build_object(
            'totalProcessed', 0,
            'newStudents', 0,
            'updatedStudents', 0,
            'newClasses', v_new_classes,
            'newEnrollments', 0
        );
    end if;

    for v_item in select * from jsonb_array_elements(v_students)
    loop
        v_masv := trim(coalesce(v_item->>'masv', ''));
        v_hoten := trim(coalesce(v_item->>'ho_ten', ''));
        v_lop := trim(coalesce(v_item->>'lop', ''));

        if length(v_masv) > 0 and length(v_hoten) > 0 and length(v_lop) > 0 then
            v_total := v_total + 1;

            -- Find class ID from batch mappings or existing classes
            select class_id into v_class_id from _batch_class_mappings where class_code = v_lop;
            if v_class_id is null then
                select id into v_class_id from public.classes where class_code = v_lop;
            end if;

            if v_class_id is null then
                raise exception 'MISSING_CLASS_MAPPING: Lớp % chưa tồn tại và chưa được ánh xạ môn học / giảng viên phụ trách.', v_lop;
            end if;

            -- Check student existence
            select id, full_name into v_existing_student_id, v_existing_name
            from public.students where student_code = v_masv;

            if v_existing_student_id is null then
                v_student_id := gen_random_uuid();
                insert into public.students (id, student_code, full_name, status, created_at)
                values (v_student_id, v_masv, v_hoten, 'ACTIVE', v_now);
                v_new_students := v_new_students + 1;

                -- Auto create student card for QR scanner
                insert into public.student_cards (id, student_id, card_code, barcode_format, is_active, created_at)
                values (gen_random_uuid(), v_student_id, 'STUDENT_' || v_masv, 'QR_CODE', true, v_now)
                on conflict (card_code) do nothing;
            else
                v_student_id := v_existing_student_id;
                if mode = 'UPDATE' and v_existing_name != v_hoten then
                    update public.students set full_name = v_hoten where id = v_student_id;
                    v_updated_students := v_updated_students + 1;
                end if;
            end if;

            -- Enrollment
            if not exists (select 1 from public.enrollments where class_id = v_class_id and student_id = v_student_id and status = 'ACTIVE') then
                insert into public.enrollments (id, class_id, student_id, status, created_at)
                values (gen_random_uuid(), v_class_id, v_student_id, 'ACTIVE', v_now)
                on conflict (class_id, student_id) do update set status = 'ACTIVE';
                v_new_enrollments := v_new_enrollments + 1;
            end if;
        end if;
    end loop;

    -- 4. Audit log
    insert into public.audit_logs (id, actor_id, action, entity_type, entity_id, old_value, new_value, reason, created_at)
    values (gen_random_uuid(), v_actor_id, 'BULK_IMPORT_STUDENTS', 'students', 'batch', '',
            'Imported ' || v_total || ' rows (' || mode || ')',
            'Nhập danh sách sinh viên hàng loạt từ CSV: ' || v_new_students || ' mới, ' || v_updated_students || ' cập nhật, ' || v_new_classes || ' lớp mới, ' || v_new_enrollments || ' phân lớp',
            v_now);

    return jsonb_build_object(
        'totalProcessed', v_total,
        'newStudents', v_new_students,
        'updatedStudents', v_updated_students,
        'newClasses', v_new_classes,
        'newEnrollments', v_new_enrollments
    );
end;
$$;

revoke all on function public.admin_import_students(jsonb, text) from public, anon;
grant execute on function public.admin_import_students(jsonb, text) to authenticated;
