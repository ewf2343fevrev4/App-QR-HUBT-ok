\set ON_ERROR_STOP on
create function public.test_assert(condition boolean,message text) returns void language plpgsql as $$ begin if condition is distinct from true then raise exception 'TEST FAILED: %',message; end if; end $$;
insert into auth.users(id,email) values
 ('00000000-0000-0000-0000-000000000001','admin@example.invalid'),
 ('00000000-0000-0000-0000-000000000002','teacher1@example.invalid'),
 ('00000000-0000-0000-0000-000000000003','teacher2@example.invalid');
update public.profiles set is_active=true,role=case when email='admin@example.invalid' then 'ADMIN' else 'LECTURER' end;
\ir ../seed.sql
set role authenticated;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';
select public.test_assert((select count(*)=3 from public.classes),'active lecturer sees all class codes');
select public.test_assert((select count(*)=0 from public.audit_logs),'no foreign audit logs');
select public.test_assert(jsonb_array_length(public.app_snapshot()->'classes')=3,'snapshot respects RLS for classes');
select public.create_attendance_session(jsonb_build_object(
  'id','40000000-0000-0000-0000-000000000004',
  'class_id','20000000-0000-0000-0000-000000000001',
  'subject_name','Lập trình Android',
  'lecturer_id','00000000-0000-0000-0000-000000000002',
  'session_name','Test session',
  'session_date','2026-10-26',
  'start_time',(extract(epoch from now())*1000)::bigint-60000,
  'late_after',(extract(epoch from now())*1000)::bigint+900000,
  'end_time',(extract(epoch from now())*1000)::bigint+7200000,
  'status','OPEN'
));
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','202610001')->>'code')='PRESENT','raw student number records present');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','unknown')->>'code')='INVALID_CARD','unknown card');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610030')->>'code')='INACTIVE_STUDENT','inactive student');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610025')->>'code')='WRONG_CLASS','wrong class');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610001')->>'code')='DUPLICATE','prefixed student number returns duplicate');
select public.adjust_attendance('40000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001','EXCUSED','Có giấy phép');
select public.test_assert(exists(select 1 from public.audit_logs where action='ADJUST_ATTENDANCE' and reason='Có giấy phép'),'manual edit audit');
do $$ begin
 begin perform public.adjust_attendance('40000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001','PRESENT',''); raise exception 'TEST FAILED: empty reason accepted'; exception when raise_exception then if sqlerrm not like 'INVALID_INPUT%' then raise; end if; end;
 begin insert into public.attendance_records(session_id,student_id,status,scanned_at,recorded_by,method) values('40000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002','PRESENT',0,'00000000-0000-0000-0000-000000000002','SCAN'); raise exception 'TEST FAILED: direct write accepted'; exception when insufficient_privilege then null; end;
 begin perform public.admin_save('students','{}'); raise exception 'TEST FAILED: lecturer admin accepted'; exception when insufficient_privilege then null; end;
end $$;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000003';
select public.test_assert((select count(*)=3 from public.classes),'other lecturer sees classes');
select public.test_assert((select count(*)=1 from public.attendance_sessions),'other lecturer only sees own sessions');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610002')->>'code')='FORBIDDEN','cross lecturer scan forbidden');
select public.test_assert((select count(*)=0 from public.attendance_records where session_id='40000000-0000-0000-0000-000000000004'),'cross lecturer records hidden');
reset role;
-- Force late cutoff in the test fixture, then exercise server-derived status.
update public.attendance_sessions set late_after=start_time where id='40000000-0000-0000-0000-000000000004';
set role authenticated;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610002')->>'code')='LATE','server time late');
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000001';
select public.admin_save('enrollments',(select to_jsonb(e)||'{"status":"INACTIVE"}'::jsonb from public.enrollments e where class_id='20000000-0000-0000-0000-000000000001' and student_id='10000000-0000-0000-0000-000000000001'));
select public.test_assert((select count(*)=20 from public.session_roster where session_id='40000000-0000-0000-0000-000000000004'),'roster immutable after enrollment change');

-- Test list_active_lecturers
set role authenticated;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002'; -- Lecturer
select public.test_assert(jsonb_array_length(public.list_active_lecturers()) = 2, 'list_active_lecturers returns active lecturers');

-- Test bulk import permissions and validations
do $$ begin
 begin
  perform public.admin_import_students('{"students":[]}'::jsonb);
  raise exception 'TEST FAILED: Lecturer cannot call bulk import';
 exception when others then
  if sqlerrm not like 'FORBIDDEN%' then raise; end if;
 end;
end $$;

set request.jwt.claim.sub='00000000-0000-0000-0000-000000000001'; -- Admin

-- 1. admin_save_student_with_class single form: only 3 fields (MASV, HoTen, Lop)
select public.test_assert(
  (public.admin_save_student_with_class('2823250124', 'Trần Quốc Bảo', 'PM28.01')->>'newStudents')::int = 1,
  'admin_save_student_with_class creates student'
);
select public.test_assert(exists(select 1 from public.classes where class_code='PM28.01'), 'class PM28.01 auto created');
select public.test_assert(exists(select 1 from public.students where student_code='2823250124'), 'student created');
select public.test_assert(not exists(select 1 from public.student_cards where card_code='STUDENT_2823250124'), 'no fake student card on cloud');

-- 2. Bulk import with only MASV, HoTen, Lop (no class_mappings required)
select public.test_assert(
  (public.admin_import_students(jsonb_build_object(
    'students', jsonb_build_array(
      jsonb_build_object('masv','2823250125','ho_ten','Đỗ Thành Công','lop','PM28.01'),
      jsonb_build_object('masv','2823250126','ho_ten','Nguyễn Văn Nam','lop','PM28.02')
    )
  ), 'SKIP')->>'newStudents')::int = 2,
  'valid import creates 2 students without class_mappings'
);
select public.test_assert(exists(select 1 from public.classes where class_code='PM28.02'), 'class PM28.02 auto created');

-- 3. Re-import in SKIP mode produces 0 new students
select public.test_assert(
  (public.admin_import_students(jsonb_build_object(
    'students', jsonb_build_array(
      jsonb_build_object('masv','2823250125','ho_ten','Đỗ Thành Công','lop','PM28.01')
    )
  ), 'SKIP')->>'newStudents')::int = 0,
  'reimport in skip mode creates 0 duplicates'
);

-- 4. Session creation requires subject_name and active lecturer
do $$ begin
 begin
  perform public.create_attendance_session(jsonb_build_object(
    'id','50000000-0000-0000-0000-000000000001',
    'class_id','20000000-0000-0000-0000-000000000001',
    'subject_name','',
    'lecturer_id','00000000-0000-0000-0000-000000000002',
    'session_name','Empty subject',
    'session_date','2026-10-26',
    'start_time',(extract(epoch from now())*1000)::bigint+1000,
    'late_after',(extract(epoch from now())*1000)::bigint+900000,
    'end_time',(extract(epoch from now())*1000)::bigint+7200000,
    'status','SCHEDULED'
  ));
  raise exception 'TEST FAILED: empty subject accepted';
 exception when others then
  if sqlerrm not like 'INVALID_INPUT: session fields%' then raise; end if;
 end;
end $$;

-- 5. Session creation rejects ADMIN as lecturer
do $$ begin
 begin
  perform public.create_attendance_session(jsonb_build_object(
    'id','50000000-0000-0000-0000-000000000002',
    'class_id','20000000-0000-0000-0000-000000000001',
    'subject_name','Môn học',
    'lecturer_id','00000000-0000-0000-0000-000000000001', -- ADMIN account
    'session_name','Admin lecturer',
    'session_date','2026-10-26',
    'start_time',(extract(epoch from now())*1000)::bigint+1000,
    'late_after',(extract(epoch from now())*1000)::bigint+900000,
    'end_time',(extract(epoch from now())*1000)::bigint+7200000,
    'status','SCHEDULED'
  ));
  raise exception 'TEST FAILED: admin as lecturer accepted';
 exception when others then
  if sqlerrm not like 'INVALID_INPUT: active lecturer required%' then raise; end if;
 end;
end $$;

-- 6. Test Server-Side Pagination RPC (admin_get_students_page)
do $$
declare
  v_page jsonb;
  v_items jsonb;
  v_total int;
  v_student jsonb;
begin
  -- As ADMIN
  v_page := public.admin_get_students_page(1, 10, null, null);
  v_items := v_page->'items';
  v_total := (v_page->>'totalItems')::int;
  if v_total < 3 then
    raise exception 'TEST FAILED: expected at least 3 students, got %', v_total;
  end if;
  if jsonb_array_length(v_items) > 10 then
    raise exception 'TEST FAILED: expected at most 10 items in page, got %', jsonb_array_length(v_items);
  end if;

  -- Test page size clamping (passing 500 should be clamped to 100)
  v_page := public.admin_get_students_page(1, 500, null, null);
  if (v_page->>'pageSize')::int <> 100 then
    raise exception 'TEST FAILED: pageSize was not clamped to 100, got %', (v_page->>'pageSize');
  end if;

  -- Test calling with only 2 parameters (p_page, p_page_size) matching Android default request
  v_page := public.admin_get_students_page(1, 20);
  if (v_page->>'pageSize')::int <> 20 or jsonb_array_length(v_page->'items') > 20 then
    raise exception 'TEST FAILED: 2-param call failed: %', v_page;
  end if;

  -- Test calling with 50 items
  v_page := public.admin_get_students_page(1, 50);
  if (v_page->>'pageSize')::int <> 50 then
    raise exception 'TEST FAILED: 50 items pageSize failed: %', v_page;
  end if;

  -- Test calling with class filter
  v_page := public.admin_get_students_page(1, 10, null, 'CNTT01');
  if jsonb_array_length(v_page->'items') = 0 then
    raise exception 'TEST FAILED: class filter CNTT01 returned 0 items';
  end if;

  -- Test search by MASV
  v_page := public.admin_get_students_page(1, 10, '202610001', null);
  if jsonb_array_length(v_page->'items') <> 1 then
    raise exception 'TEST FAILED: search by student_code failed, got %', jsonb_array_length(v_page->'items');
  end if;

  -- Test get_student_by_id
  v_student := public.get_student_by_id('10000000-0000-0000-0000-000000000001');
  if v_student->>'student_code' <> '202610001' then
    raise exception 'TEST FAILED: get_student_by_id returned wrong student: %', v_student;
  end if;
end $$;

-- 7. Test Lecturer cannot call admin_get_students_page
do $$
begin
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true); -- LECTURER
  begin
    perform public.admin_get_students_page(1, 10, null, null);
    raise exception 'TEST FAILED: Lecturer was able to call admin_get_students_page';
  exception when others then
    if sqlerrm not like 'FORBIDDEN%' then raise; end if;
  end;
  -- Restore ADMIN claim
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
end $$;

-- 8. Test Bulk Import in UPDATE mode with class transfer (3-field format, no class_mappings)
do $$
declare
  v_res jsonb;
  v_st_id uuid;
  v_cls_a uuid;
  v_cls_b uuid;
  v_status_a text;
  v_status_b text;
  v_name text;
begin
  -- First import: student in TEST_CLASS_A
  v_res := public.admin_import_students(
    jsonb_build_object('students', jsonb_build_array(
      jsonb_build_object('masv', 'TEST_MASV_01', 'ho_ten', 'Nguyen Van A', 'lop', 'TEST_CLASS_A')
    )),
    'UPDATE'
  );
  if (v_res->>'newStudents')::int <> 1 then
    raise exception 'TEST FAILED: expected 1 new student, got %', v_res;
  end if;

  select id, full_name into v_st_id, v_name from public.students where student_code = 'TEST_MASV_01';
  select id into v_cls_a from public.classes where class_code = 'TEST_CLASS_A';
  select status into v_status_a from public.enrollments where student_id = v_st_id and class_id = v_cls_a;
  if v_status_a <> 'ACTIVE' then
    raise exception 'TEST FAILED: student should be ACTIVE in TEST_CLASS_A';
  end if;

  -- Second import: student transfers to TEST_CLASS_B with updated name
  v_res := public.admin_import_students(
    jsonb_build_object('students', jsonb_build_array(
      jsonb_build_object('masv', 'TEST_MASV_01', 'ho_ten', 'Nguyen Van A Updated', 'lop', 'TEST_CLASS_B')
    )),
    'UPDATE'
  );
  if (v_res->>'updatedStudents')::int <> 1 or (v_res->>'newStudents')::int <> 0 then
    raise exception 'TEST FAILED: expected 0 new and 1 updated student, got %', v_res;
  end if;

  select id into v_cls_b from public.classes where class_code = 'TEST_CLASS_B';
  select status into v_status_a from public.enrollments where student_id = v_st_id and class_id = v_cls_a;
  select status into v_status_b from public.enrollments where student_id = v_st_id and class_id = v_cls_b;
  select full_name into v_name from public.students where id = v_st_id;

  if v_name <> 'Nguyen Van A Updated' then
    raise exception 'TEST FAILED: student name was not updated, got %', v_name;
  end if;
  if v_status_a <> 'INACTIVE' then
    raise exception 'TEST FAILED: old class enrollment should be INACTIVE, got %', v_status_a;
  end if;
  if v_status_b <> 'ACTIVE' then
    raise exception 'TEST FAILED: new class enrollment should be ACTIVE, got %', v_status_b;
  end if;

  -- Third import: identical data in UPDATE mode produces 0 duplicates and 0 new students
  v_res := public.admin_import_students(
    jsonb_build_object('students', jsonb_build_array(
      jsonb_build_object('masv', 'TEST_MASV_01', 'ho_ten', 'Nguyen Van A Updated', 'lop', 'TEST_CLASS_B')
    )),
    'UPDATE'
  );
  if (v_res->>'newStudents')::int <> 0 or (v_res->>'updatedStudents')::int <> 0 then
    raise exception 'TEST FAILED: re-import produced unexpected new/updated students, got %', v_res;
  end if;
end $$;

-- ----------------------------------------------------------------------------
-- Tests for Migration 006: Fix Login & QR Scan
-- ----------------------------------------------------------------------------
reset role;

update public.attendance_sessions
set status = 'OPEN',
    start_time = (extract(epoch from clock_timestamp())*1000)::bigint - 60000,
    late_after = (extract(epoch from clock_timestamp())*1000)::bigint + 900000,
    end_time = (extract(epoch from clock_timestamp())*1000)::bigint + 3600000
where id = '40000000-0000-0000-0000-000000000004';

delete from public.attendance_records where session_id = '40000000-0000-0000-0000-000000000004';

-- Future session fixture
insert into public.attendance_sessions (id, class_id, session_name, session_date, start_time, late_after, end_time, status, created_by, subject_name, lecturer_id)
values ('40000000-0000-0000-0000-000000000099', '20000000-0000-0000-0000-000000000001', 'Future Session', '2026-10-27',
        (extract(epoch from clock_timestamp())*1000)::bigint + 3600000,
        (extract(epoch from clock_timestamp())*1000)::bigint + 4500000,
        (extract(epoch from clock_timestamp())*1000)::bigint + 7200000,
        'OPEN', '00000000-0000-0000-0000-000000000002', 'Lập trình Android', '00000000-0000-0000-0000-000000000002')
on conflict (id) do update set status = 'OPEN', start_time = excluded.start_time, end_time = excluded.end_time;
insert into public.session_roster(session_id, student_id) values ('40000000-0000-0000-0000-000000000099', '10000000-0000-0000-0000-000000000001') on conflict do nothing;

-- Expired session fixture
insert into public.attendance_sessions (id, class_id, session_name, session_date, start_time, late_after, end_time, status, created_by, subject_name, lecturer_id)
values ('40000000-0000-0000-0000-000000000098', '20000000-0000-0000-0000-000000000001', 'Expired Session', '2026-10-25',
        (extract(epoch from clock_timestamp())*1000)::bigint - 7200000,
        (extract(epoch from clock_timestamp())*1000)::bigint - 5400000,
        (extract(epoch from clock_timestamp())*1000)::bigint - 1000,
        'OPEN', '00000000-0000-0000-0000-000000000002', 'Lập trình Android', '00000000-0000-0000-0000-000000000002')
on conflict (id) do update set status = 'OPEN', start_time = excluded.start_time, end_time = excluded.end_time;
insert into public.session_roster(session_id, student_id) values ('40000000-0000-0000-0000-000000000098', '10000000-0000-0000-0000-000000000001') on conflict do nothing;

-- Closed session fixture
insert into public.attendance_sessions (id, class_id, session_name, session_date, start_time, late_after, end_time, status, created_by, subject_name, lecturer_id)
values ('40000000-0000-0000-0000-000000000097', '20000000-0000-0000-0000-000000000001', 'Closed Session', '2026-10-26',
        (extract(epoch from clock_timestamp())*1000)::bigint - 60000,
        (extract(epoch from clock_timestamp())*1000)::bigint + 900000,
        (extract(epoch from clock_timestamp())*1000)::bigint + 3600000,
        'CLOSED', '00000000-0000-0000-0000-000000000002', 'Lập trình Android', '00000000-0000-0000-0000-000000000002')
on conflict (id) do update set status = 'CLOSED';
insert into public.session_roster(session_id, student_id) values ('40000000-0000-0000-0000-000000000097', '10000000-0000-0000-0000-000000000001') on conflict do nothing;

-- Switch to authenticated Lecturer to test record_attendance RPC
set role authenticated;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';

select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','202610001')->>'code')='PRESENT','Test 1: Raw numeric MASV records PRESENT');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','202610001')->>'code')='DUPLICATE','Test 2: Duplicate scan returns DUPLICATE');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610002')->>'code')='PRESENT','Test 3: Prefixed STUDENT_ scan records PRESENT');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','9999999999')->>'code')='INVALID_CARD','Test 4: Non-existent card returns INVALID_CARD');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','202610030')->>'code')='INACTIVE_STUDENT','Test 5: Inactive student returns INACTIVE_STUDENT');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','202610025')->>'code')='WRONG_CLASS','Test 6: Student from other class returns WRONG_CLASS');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000099','202610001')->>'code')='NOT_STARTED','Test 7: Future session returns NOT_STARTED');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000098','202610001')->>'code')='SESSION_EXPIRED','Test 8: Expired session returns SESSION_EXPIRED');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000097','202610001')->>'code')='SESSION_CLOSED','Test 9: Closed session returns SESSION_CLOSED');

reset role;
set role anon;
do $$ begin
 begin perform public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610003'); raise exception 'TEST FAILED: anonymous RPC accepted'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select 'Sequential integration assertions PASSED' as result;
