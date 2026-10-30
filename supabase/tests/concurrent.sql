\set ON_ERROR_STOP on
set role authenticated;
set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';
select public.record_attendance('40000000-0000-0000-0000-000000000004','STUDENT_202610003')->>'code';
