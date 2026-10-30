-- Smart Attendance. Millisecond UTC instants match Kotlin Long exactly.
begin;
create function public.now_ms() returns bigint language sql volatile set search_path='' as $$ select (extract(epoch from clock_timestamp()) * 1000)::bigint $$;

create table public.profiles (
 id uuid primary key references auth.users(id) on delete restrict,
 full_name text not null check(length(trim(full_name))>0), email text not null unique,
 role text not null default 'LECTURER' check(role in ('ADMIN','LECTURER')),
 is_active boolean not null default false, created_at bigint not null default public.now_ms()
);
create table public.students (
 id uuid primary key default gen_random_uuid(), student_code text not null unique check(student_code ~ '^[A-Za-z0-9_-]{3,40}$'),
 full_name text not null check(length(trim(full_name))>0), date_of_birth text not null default '', gender text not null default '',
 email text not null default '', phone text not null default '', avatar_url text not null default '',
 status text not null default 'ACTIVE' check(status in ('ACTIVE','INACTIVE')), created_at bigint not null default public.now_ms()
);
create table public.student_cards (
 id uuid primary key default gen_random_uuid(), student_id uuid not null references public.students(id) on delete restrict,
 card_code text not null unique check(length(card_code) between 1 and 512),
 barcode_format text not null default 'QR_CODE' check(barcode_format in ('QR_CODE','CODE_128','CODE_39','EAN_13')),
 is_active boolean not null default true, created_at bigint not null default public.now_ms()
);
create table public.classes (
 id uuid primary key default gen_random_uuid(), class_code text not null unique check(length(trim(class_code))>0),
 class_name text not null check(length(trim(class_name))>0), subject_name text not null check(length(trim(subject_name))>0),
 lecturer_id uuid not null references public.profiles(id) on delete restrict,
 semester text not null, academic_year text not null, created_at bigint not null default public.now_ms()
);
create table public.enrollments (
 id uuid primary key default gen_random_uuid(), class_id uuid not null references public.classes(id) on delete restrict,
 student_id uuid not null references public.students(id) on delete restrict,
 status text not null default 'ACTIVE' check(status in ('ACTIVE','INACTIVE')), created_at bigint not null default public.now_ms(),
 unique(class_id,student_id)
);
create table public.attendance_sessions (
 id uuid primary key default gen_random_uuid(), class_id uuid not null references public.classes(id) on delete restrict,
 session_name text not null check(length(trim(session_name))>0), session_date text not null check(session_date ~ '^\d{4}-\d{2}-\d{2}$'),
 start_time bigint not null, late_after bigint not null, end_time bigint not null,
 status text not null default 'SCHEDULED' check(status in ('SCHEDULED','OPEN','CLOSED')),
 created_by uuid not null references public.profiles(id) on delete restrict, created_at bigint not null default public.now_ms(),
 check(start_time <= late_after and late_after < end_time)
);
create table public.session_roster (
 session_id uuid not null references public.attendance_sessions(id) on delete restrict,
 student_id uuid not null references public.students(id) on delete restrict, primary key(session_id,student_id)
);
create table public.attendance_records (
 id uuid primary key default gen_random_uuid(), session_id uuid not null, student_id uuid not null,
 status text not null check(status in ('PRESENT','LATE','EXCUSED')), scanned_at bigint not null,
 recorded_by uuid not null references public.profiles(id) on delete restrict,
 method text not null check(method in ('SCAN','MANUAL','SEED')), note text not null default '', created_at bigint not null default public.now_ms(),
 unique(session_id,student_id), foreign key(session_id,student_id) references public.session_roster(session_id,student_id) on delete restrict
);
create table public.audit_logs (
 id uuid primary key default gen_random_uuid(), actor_id uuid not null references public.profiles(id) on delete restrict,
 action text not null, entity_type text not null, entity_id text not null,
 old_value text not null default '', new_value text not null default '', reason text not null default '', created_at bigint not null default public.now_ms()
);
create index on public.student_cards(student_id);
create index on public.classes(lecturer_id);
create index on public.enrollments(student_id);
create index on public.attendance_sessions(class_id,start_time desc);
create index on public.attendance_sessions(created_by);
create index on public.session_roster(student_id);
create index on public.attendance_records(student_id);
create index on public.attendance_records(recorded_by);
create index on public.audit_logs(actor_id,created_at desc);

create function public.handle_new_user() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.profiles(id,full_name,email) values(new.id,coalesce(nullif(trim(new.raw_user_meta_data->>'full_name'),''),'Giảng viên'),new.email);
 return new;
end $$;
create trigger attendance_new_user after insert on auth.users for each row execute function public.handle_new_user();

create function public.is_admin() returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles where id=auth.uid() and is_active and role='ADMIN')
$$;
create function public.can_manage(p_class_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles p join public.classes c on c.id=p_class_id where p.id=auth.uid() and p.is_active and (p.role='ADMIN' or c.lecturer_id=p.id))
$$;
create function public.can_view_student(p_student_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select public.is_admin() or exists(select 1 from public.enrollments e where e.student_id=p_student_id and public.can_manage(e.class_id))
 or exists(select 1 from public.session_roster r join public.attendance_sessions s on s.id=r.session_id where r.student_id=p_student_id and public.can_manage(s.class_id))
$$;
alter table public.profiles enable row level security;
alter table public.students enable row level security;
alter table public.student_cards enable row level security;
alter table public.classes enable row level security;
alter table public.enrollments enable row level security;
alter table public.attendance_sessions enable row level security;
alter table public.session_roster enable row level security;
alter table public.attendance_records enable row level security;
alter table public.audit_logs enable row level security;
create policy profile_read on public.profiles for select to authenticated using(id=auth.uid() or public.is_admin());
create policy student_read on public.students for select to authenticated using(public.can_view_student(id));
create policy card_read on public.student_cards for select to authenticated using(public.can_view_student(student_id));
create policy class_read on public.classes for select to authenticated using(public.can_manage(id));
create policy enrollment_read on public.enrollments for select to authenticated using(public.can_manage(class_id));
create policy session_read on public.attendance_sessions for select to authenticated using(public.can_manage(class_id));
create policy roster_read on public.session_roster for select to authenticated using(exists(select 1 from public.attendance_sessions s where s.id=session_id and public.can_manage(s.class_id)));
create policy record_read on public.attendance_records for select to authenticated using(exists(select 1 from public.attendance_sessions s where s.id=session_id and public.can_manage(s.class_id)));
create policy audit_read on public.audit_logs for select to authenticated using(public.is_admin() or (actor_id=auth.uid() and exists(select 1 from public.profiles p where p.id=auth.uid() and p.is_active)));

-- Snapshot is SECURITY INVOKER: every SELECT continues to enforce RLS.
create function public.app_snapshot() returns jsonb language plpgsql stable security invoker set search_path='' as $$
begin
 if not exists(select 1 from public.profiles where id=auth.uid() and is_active) then raise exception 'INACTIVE_ACCOUNT' using errcode='42501'; end if;
 return jsonb_build_object(
  'profiles',coalesce((select jsonb_agg(to_jsonb(t)) from public.profiles t),'[]'),
  'students',coalesce((select jsonb_agg(to_jsonb(t)) from public.students t),'[]'),
  'cards',coalesce((select jsonb_agg(to_jsonb(t)) from public.student_cards t),'[]'),
  'classes',coalesce((select jsonb_agg(to_jsonb(t)) from public.classes t),'[]'),
  'enrollments',coalesce((select jsonb_agg(to_jsonb(t)) from public.enrollments t),'[]'),
  'sessions',coalesce((select jsonb_agg(to_jsonb(t)) from public.attendance_sessions t),'[]'),
  'roster',coalesce((select jsonb_agg(to_jsonb(t)) from public.session_roster t),'[]'),
  'records',coalesce((select jsonb_agg(to_jsonb(t)) from public.attendance_records t),'[]'),
  'logs',coalesce((select jsonb_agg(to_jsonb(t)) from (select * from public.audit_logs order by created_at desc limit 200) t),'[]'));
end $$;

create function public.create_attendance_session(payload jsonb) returns void language plpgsql security definer set search_path='' as $$
declare s public.attendance_sessions;
begin
 s:=jsonb_populate_record(null::public.attendance_sessions,payload);
 perform 1 from public.classes where id=s.class_id for update;
 if not public.can_manage(s.class_id) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if s.status not in ('SCHEDULED','OPEN') or s.status is null or s.end_time<=public.now_ms() then raise exception 'INVALID_INPUT'; end if;
 perform s.session_date::date;
 s.created_by:=auth.uid(); s.created_at:=public.now_ms();
 insert into public.attendance_sessions select s.*;
 insert into public.session_roster(session_id,student_id) select s.id,e.student_id from public.enrollments e join public.students st on st.id=e.student_id where e.class_id=s.class_id and e.status='ACTIVE' and st.status='ACTIVE';
 insert into public.audit_logs(actor_id,action,entity_type,entity_id,new_value) values(auth.uid(),'CREATE_SESSION','attendance_sessions',s.id::text,to_jsonb(s)::text);
end $$;

create function public.set_session_status(p_session_id uuid,p_status text) returns void language plpgsql security definer set search_path='' as $$
declare s public.attendance_sessions;
begin
 select * into s from public.attendance_sessions where id=p_session_id for update;
 if not found or not public.can_manage(s.class_id) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if p_status is null or p_status not in ('OPEN','CLOSED') or s.status='CLOSED' or (p_status='OPEN' and public.now_ms()>=s.end_time) then raise exception 'SESSION_CLOSED'; end if;
 update public.attendance_sessions set status=p_status where id=s.id;
 insert into public.audit_logs(actor_id,action,entity_type,entity_id,old_value,new_value) values(auth.uid(),'SESSION_STATUS','attendance_sessions',s.id::text,to_jsonb(s)::text,(select to_jsonb(t)::text from public.attendance_sessions t where id=s.id));
end $$;

create function public.record_attendance(p_session_id uuid,p_card_code text) returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.attendance_sessions; c public.student_cards; st public.students; r public.attendance_records; t bigint;
begin
 select * into s from public.attendance_sessions where id=p_session_id for update;
 if not found or not public.can_manage(s.class_id) then return jsonb_build_object('code','FORBIDDEN'); end if;
 t:=public.now_ms();
 if s.status<>'OPEN' or t<s.start_time or t>=s.end_time then return jsonb_build_object('code','SESSION_CLOSED'); end if;
 if p_card_code is null or length(p_card_code)>512 then return jsonb_build_object('code','INVALID_CARD'); end if;
 select * into c from public.student_cards where card_code=p_card_code and is_active for share;
 if not found then return jsonb_build_object('code','INVALID_CARD'); end if;
 select * into st from public.students where id=c.student_id for share;
 if st.status<>'ACTIVE' then return jsonb_build_object('code','INACTIVE_STUDENT'); end if;
 if not exists(select 1 from public.session_roster where session_id=s.id and student_id=st.id) then return jsonb_build_object('code','WRONG_CLASS'); end if;
 select * into r from public.attendance_records where session_id=s.id and student_id=st.id;
 if found then return jsonb_build_object('code','DUPLICATE','student',to_jsonb(st),'record',to_jsonb(r)); end if;
 insert into public.attendance_records(session_id,student_id,status,scanned_at,recorded_by,method)
 values(s.id,st.id,case when t>=s.late_after then 'LATE' else 'PRESENT' end,t,auth.uid(),'SCAN') returning * into r;
 return jsonb_build_object('code',r.status,'student',to_jsonb(st),'record',to_jsonb(r));
end $$;

create function public.adjust_attendance(p_session_id uuid,p_student_id uuid,p_status text,p_reason text) returns void language plpgsql security definer set search_path='' as $$
declare s public.attendance_sessions; old_record public.attendance_records; new_record public.attendance_records;
begin
 select * into s from public.attendance_sessions where id=p_session_id for update;
 if not found or not public.can_manage(s.class_id) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if p_reason is null or length(trim(p_reason))<3 or p_status is null or p_status not in ('PRESENT','LATE','EXCUSED','ABSENT') or not exists(select 1 from public.session_roster where session_id=s.id and student_id=p_student_id) then raise exception 'INVALID_INPUT'; end if;
 select * into old_record from public.attendance_records where session_id=s.id and student_id=p_student_id;
 if p_status='ABSENT' then delete from public.attendance_records where session_id=s.id and student_id=p_student_id;
 else
  insert into public.attendance_records(session_id,student_id,status,scanned_at,recorded_by,method,note)
  values(s.id,p_student_id,p_status,public.now_ms(),auth.uid(),'MANUAL',trim(p_reason))
  on conflict(session_id,student_id) do update set status=excluded.status,recorded_by=excluded.recorded_by,method='MANUAL',note=excluded.note returning * into new_record;
 end if;
 insert into public.audit_logs(actor_id,action,entity_type,entity_id,old_value,new_value,reason)
 values(auth.uid(),'ADJUST_ATTENDANCE','attendance_records',s.id::text||'/'||p_student_id::text,coalesce(to_jsonb(old_record)::text,''),coalesce(to_jsonb(new_record)::text,''),trim(p_reason));
end $$;

-- Explicit allowlist and typed records: no dynamic SQL from client data.
create function public.admin_save(p_entity text,payload jsonb) returns void language plpgsql security definer set search_path='' as $$
declare st public.students; cl public.classes; ca public.student_cards; en public.enrollments; pr public.profiles; old_data text:=''; new_data text; entity_id uuid;
begin
 if not public.is_admin() then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 entity_id:=(payload->>'id')::uuid;
 case p_entity
 when 'students' then
  st:=jsonb_populate_record(null::public.students,payload); st.created_at:=public.now_ms();
  select to_jsonb(t)::text into old_data from public.students t where id=entity_id for update;
  insert into public.students select st.* on conflict(id) do update set student_code=excluded.student_code,full_name=excluded.full_name,date_of_birth=excluded.date_of_birth,gender=excluded.gender,email=excluded.email,phone=excluded.phone,avatar_url=excluded.avatar_url,status=excluded.status;
  select to_jsonb(t)::text into new_data from public.students t where id=entity_id;
 when 'classes' then
  cl:=jsonb_populate_record(null::public.classes,payload);cl.created_at:=public.now_ms();
  if not exists(select 1 from public.profiles where id=cl.lecturer_id and is_active) then raise exception 'INVALID_INPUT'; end if;
  select to_jsonb(t)::text into old_data from public.classes t where id=entity_id for update;
  insert into public.classes select cl.* on conflict(id) do update set class_code=excluded.class_code,class_name=excluded.class_name,subject_name=excluded.subject_name,lecturer_id=excluded.lecturer_id,semester=excluded.semester,academic_year=excluded.academic_year;
  select to_jsonb(t)::text into new_data from public.classes t where id=entity_id;
 when 'student_cards' then
  ca:=jsonb_populate_record(null::public.student_cards,payload);ca.created_at:=public.now_ms();
  select to_jsonb(t)::text into old_data from public.student_cards t where id=entity_id for update;
  insert into public.student_cards select ca.* on conflict(id) do update set student_id=excluded.student_id,card_code=excluded.card_code,barcode_format=excluded.barcode_format,is_active=excluded.is_active;
  select to_jsonb(t)::text into new_data from public.student_cards t where id=entity_id;
 when 'enrollments' then
  en:=jsonb_populate_record(null::public.enrollments,payload);en.created_at:=public.now_ms();
  perform 1 from public.classes where id=en.class_id for update;
  select to_jsonb(t)::text into old_data from public.enrollments t where id=entity_id for update;
  insert into public.enrollments select en.* on conflict(id) do update set class_id=excluded.class_id,student_id=excluded.student_id,status=excluded.status;
  select to_jsonb(t)::text into new_data from public.enrollments t where id=entity_id;
 when 'profiles' then
  pr:=jsonb_populate_record(null::public.profiles,payload);
  if entity_id=auth.uid() and (pr.role<>'ADMIN' or not pr.is_active) then raise exception 'INVALID_INPUT'; end if;
  select to_jsonb(t)::text into old_data from public.profiles t where id=entity_id for update;
  if not found then raise exception 'INVALID_INPUT: create Auth account first'; end if;
  update public.profiles set full_name=pr.full_name,role=pr.role,is_active=pr.is_active where id=entity_id;
  select to_jsonb(t)::text into new_data from public.profiles t where id=entity_id;
 else raise exception 'INVALID_INPUT';
 end case;
 insert into public.audit_logs(actor_id,action,entity_type,entity_id,old_value,new_value,reason) values(auth.uid(),'ADMIN_SAVE',p_entity,entity_id::text,coalesce(old_data,''),new_data,'Quản lý dữ liệu');
end $$;

-- No direct client writes, including admins; all writes go through validated RPC.
revoke all on table public.profiles,public.students,public.student_cards,public.classes,public.enrollments,public.attendance_sessions,public.session_roster,public.attendance_records,public.audit_logs from anon,authenticated;
grant select on table public.profiles,public.students,public.student_cards,public.classes,public.enrollments,public.attendance_sessions,public.session_roster,public.attendance_records,public.audit_logs to authenticated;
revoke all on function public.now_ms(),public.handle_new_user(),public.is_admin(),public.can_manage(uuid),public.can_view_student(uuid),public.app_snapshot(),public.create_attendance_session(jsonb),public.set_session_status(uuid,text),public.record_attendance(uuid,text),public.adjust_attendance(uuid,uuid,text,text),public.admin_save(text,jsonb) from public,anon;
grant execute on function public.is_admin(),public.can_manage(uuid),public.can_view_student(uuid),public.app_snapshot(),public.create_attendance_session(jsonb),public.set_session_status(uuid,text),public.record_attendance(uuid,text),public.adjust_attendance(uuid,uuid,text,text) ,public.admin_save(text,jsonb) to authenticated;
commit;
