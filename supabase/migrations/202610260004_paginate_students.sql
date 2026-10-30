-- Migration: 202610260004_paginate_students.sql
-- Description: True server-side pagination, trigram indexes, and secure RPCs for student administration
BEGIN;

-- 1. Enable pg_trgm extension for fast substring / ILIKE search on full_name and student_code
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Indexes for stable pagination, sorting, search, and joins
CREATE INDEX IF NOT EXISTS students_code_asc_idx ON public.students(student_code ASC, id ASC);
CREATE INDEX IF NOT EXISTS students_name_trgm_idx ON public.students USING gin (full_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS students_code_trgm_idx ON public.students USING gin (student_code gin_trgm_ops);
CREATE INDEX IF NOT EXISTS classes_class_code_idx ON public.classes(class_code);
CREATE INDEX IF NOT EXISTS enrollments_student_class_active_idx ON public.enrollments(student_id, class_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS enrollments_class_student_active_idx ON public.enrollments(class_id, student_id) WHERE status = 'ACTIVE';

-- 3. Dedicated Server-Side Paginated RPC for Student Administration
CREATE OR REPLACE FUNCTION public.admin_get_students_page(
    p_page integer DEFAULT 1,
    p_page_size integer DEFAULT 10,
    p_search text DEFAULT NULL,
    p_class_code text DEFAULT NULL
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    v_actor_role text;
    v_safe_page integer;
    v_safe_size integer;
    v_offset integer;
    v_total integer;
    v_total_pages integer;
    v_search text;
    v_class_code text;
    v_items jsonb;
BEGIN
    -- Strict authorization check: Only active ADMIN can access student administration
    SELECT role INTO v_actor_role
    FROM public.profiles
    WHERE id = auth.uid() AND is_active = true;

    IF v_actor_role IS NULL OR v_actor_role <> 'ADMIN' THEN
        RAISE EXCEPTION 'FORBIDDEN: Chỉ ADMIN mới có quyền truy cập danh sách quản trị sinh viên.' USING errcode = '42501';
    END IF;

    -- Clamp pagination inputs
    v_safe_page := GREATEST(COALESCE(p_page, 1), 1);
    v_safe_size := LEAST(GREATEST(COALESCE(p_page_size, 10), 1), 100); -- Clamp strictly between 1 and 100
    v_offset := (v_safe_page - 1) * v_safe_size;

    v_search := NULLIF(TRIM(p_search), '');
    v_class_code := NULLIF(TRIM(p_class_code), '');
    IF v_class_code = 'Tất cả' THEN
        v_class_code := NULL;
    END IF;

    -- Calculate total count with filter conditions
    SELECT COUNT(DISTINCT s.id)
    INTO v_total
    FROM public.students s
    LEFT JOIN public.enrollments e ON e.student_id = s.id AND e.status = 'ACTIVE'
    LEFT JOIN public.classes c ON c.id = e.class_id
    WHERE (v_class_code IS NULL OR c.class_code = v_class_code)
      AND (v_search IS NULL OR (
          s.student_code ILIKE '%' || v_search || '%' OR
          s.full_name ILIKE '%' || v_search || '%' OR
          c.class_code ILIKE '%' || v_search || '%'
      ));

    v_total_pages := CASE WHEN v_total = 0 THEN 1 ELSE CEIL(v_total::numeric / v_safe_size)::integer END;

    -- Query only the requested page items
    SELECT COALESCE(jsonb_agg(sub.item), '[]'::jsonb)
    INTO v_items
    FROM (
        SELECT jsonb_build_object(
            'id', s.id,
            'student_code', s.student_code,
            'full_name', s.full_name,
            'status', s.status,
            'created_at', s.created_at,
            'class_code', COALESCE(string_agg(DISTINCT c.class_code, ', ' ORDER BY c.class_code), '')
        ) AS item
        FROM public.students s
        LEFT JOIN public.enrollments e ON e.student_id = s.id AND e.status = 'ACTIVE'
        LEFT JOIN public.classes c ON c.id = e.class_id
        WHERE (v_class_code IS NULL OR c.class_code = v_class_code)
          AND (v_search IS NULL OR (
              s.student_code ILIKE '%' || v_search || '%' OR
              s.full_name ILIKE '%' || v_search || '%' OR
              c.class_code ILIKE '%' || v_search || '%'
          ))
        GROUP BY s.id, s.student_code, s.full_name, s.status, s.created_at
        ORDER BY s.student_code ASC, s.id ASC
        LIMIT v_safe_size OFFSET v_offset
    ) sub;

    RETURN jsonb_build_object(
        'items', v_items,
        'page', v_safe_page,
        'pageSize', v_safe_size,
        'totalItems', v_total,
        'totalPages', v_total_pages,
        'hasNextPage', (v_safe_page < v_total_pages),
        'hasPreviousPage', (v_safe_page > 1)
    );
END;
$$;

-- 4. Get full student detail by ID (for edit/view)
CREATE OR REPLACE FUNCTION public.get_student_by_id(p_student_id uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    v_actor_role text;
    v_result jsonb;
BEGIN
    SELECT role INTO v_actor_role
    FROM public.profiles
    WHERE id = auth.uid() AND is_active = true;

    IF v_actor_role IS NULL THEN
        RAISE EXCEPTION 'FORBIDDEN: Tài khoản chưa được kích hoạt.' USING errcode = '42501';
    END IF;

    SELECT jsonb_build_object(
        'id', s.id,
        'student_code', s.student_code,
        'full_name', s.full_name,
        'date_of_birth', COALESCE(s.date_of_birth::text, ''),
        'gender', COALESCE(s.gender, ''),
        'email', COALESCE(s.email, ''),
        'phone', COALESCE(s.phone, ''),
        'avatar_url', COALESCE(s.avatar_url, ''),
        'status', s.status,
        'created_at', s.created_at
    ) INTO v_result
    FROM public.students s
    WHERE s.id = p_student_id;

    RETURN v_result;
END;
$$;

-- 5. Revoke / Grant permissions
REVOKE ALL ON FUNCTION public.admin_get_students_page(integer, integer, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_get_students_page(integer, integer, text, text) TO authenticated;

REVOKE ALL ON FUNCTION public.get_student_by_id(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_student_by_id(uuid) TO authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;
