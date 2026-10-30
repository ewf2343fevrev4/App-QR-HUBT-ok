\set ON_ERROR_STOP on
select public.test_assert((select count(*)=1 from public.attendance_records where session_id='40000000-0000-0000-0000-000000000004' and student_id='10000000-0000-0000-0000-000000000003'),'concurrent requests yield exactly one row');
set role authenticated;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';
select public.set_session_status('40000000-0000-0000-0000-000000000004','CLOSED');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610004')->>'code')='SESSION_CLOSED','closed session rejects scan');
reset role;
update public.profiles set is_active=false where id='00000000-0000-0000-0000-000000000002';
set role authenticated;
select public.test_assert((select count(*)=0 from public.classes),'disabled account loses access');
select public.test_assert((public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610004')->>'code')='FORBIDDEN','disabled account cannot scan');
reset role;
select 'Concurrent, closed-session and disabled-account assertions PASSED' as result;
