-- Run explicitly on a DEVELOPMENT project after creating/activating two lecturers.
-- Does not create passwords, Auth users, or a privileged account.
begin;
do $$
declare lecturers uuid[]; cid uuid; sid uuid; n integer; k integer; t bigint:=public.now_ms();
begin
 select array_agg(id order by email) into lecturers from public.profiles where is_active and role='LECTURER';
 if coalesce(array_length(lecturers,1),0)<2 then raise exception 'Create and activate at least two LECTURER Auth accounts before seed.sql'; end if;
 for n in 1..30 loop
  sid:=('10000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid;
  insert into public.students(id,student_code,full_name,status) values(sid,'20261'||lpad(n::text,4,'0'),'Sinh viên mẫu '||n,case when n=30 then 'INACTIVE' else 'ACTIVE' end) on conflict do nothing;
  insert into public.student_cards(id,student_id,card_code) values(('30000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,sid,'STUDENT_20261'||lpad(n::text,4,'0')) on conflict do nothing;
 end loop;
 insert into public.student_cards(id,student_id,card_code,barcode_format) values
 ('30000000-0000-0000-0000-000000000031','10000000-0000-0000-0000-000000000001','STUDENT-202610001','CODE_39'),
 ('30000000-0000-0000-0000-000000000032','10000000-0000-0000-0000-000000000002','5901234123457','EAN_13') on conflict do nothing;
 for k in 1..3 loop
  cid:=('20000000-0000-0000-0000-'||lpad(k::text,12,'0'))::uuid;
  insert into public.classes(id,class_code,class_name,subject_name,lecturer_id,semester,academic_year)
  values(cid,'CNTT0'||k,'Công nghệ thông tin 0'||k,(array['Lập trình Android','Cơ sở dữ liệu','Kỹ thuật phần mềm'])[k],lecturers[case when k=3 then 2 else 1 end],'1','2026–2027') on conflict do nothing;
  for n in (case k when 1 then 1 when 2 then 11 else 21 end)..(case k when 1 then 20 else 29 end) loop
   sid:=('10000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid;
   insert into public.enrollments(class_id,student_id) values(cid,sid) on conflict do nothing;
  end loop;
  sid:=('40000000-0000-0000-0000-'||lpad(k::text,12,'0'))::uuid;
  insert into public.attendance_sessions(id,class_id,session_name,session_date,start_time,late_after,end_time,status,created_by,subject_name,lecturer_id)
  values(sid,cid,'Buổi mẫu • Tuần 1',to_char(current_date-1,'YYYY-MM-DD'),t-86400000,t-85500000,t-81000000,'CLOSED',lecturers[case when k=3 then 2 else 1 end],(array['Lập trình Android','Cơ sở dữ liệu','Kỹ thuật phần mềm'])[k],lecturers[case when k=3 then 2 else 1 end]) on conflict do nothing;
  insert into public.session_roster select sid,student_id from public.enrollments where class_id=cid on conflict do nothing;
  insert into public.attendance_records(session_id,student_id,status,scanned_at,recorded_by,method,note)
  select sid,student_id,'PRESENT',t-86000000,lecturers[case when k=3 then 2 else 1 end],'SEED','Bản ghi mẫu để trình diễn' from public.enrollments where class_id=cid order by student_id limit 7 on conflict do nothing;
 end loop;
end $$;
commit;
