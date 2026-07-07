-- 奢享家 HR AI 面试系统统一初始化 SQL
-- 数据库：MySQL 8.x
-- 说明：
-- 1. 该脚本不由程序自动执行，请在目标数据库中手动执行。
-- 2. 新环境直接执行本文件即可完成建库、建表、基础配置和权限初始化。
-- 3. 已执行过旧版 SQL 的环境，也优先重复执行本文件，脚本会尽量补齐缺失字段和索引。

create database if not exists hr_interview
    default character set utf8mb4
    collate utf8mb4_0900_ai_ci;

use hr_interview;

drop procedure if exists add_column_if_missing;
drop procedure if exists add_index_if_missing;

delimiter //

create procedure add_column_if_missing(
    in p_table_name varchar(64),
    in p_column_name varchar(64),
    in p_alter_sql text
)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = p_table_name
          and column_name = p_column_name
    ) then
        set @ddl = p_alter_sql;
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //

create procedure add_index_if_missing(
    in p_table_name varchar(64),
    in p_index_name varchar(64),
    in p_create_sql text
)
begin
    if not exists (
        select 1
        from information_schema.statistics
        where table_schema = database()
          and table_name = p_table_name
          and index_name = p_index_name
    ) then
        set @ddl = p_create_sql;
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //

delimiter ;

create table if not exists hr_user (
    id bigint not null auto_increment comment '用户ID',
    name varchar(100) not null comment '用户姓名',
    email varchar(200) not null comment '邮箱',
    mobile varchar(32) null comment '手机号',
    wecom_userid varchar(100) null comment '企业微信成员UserID',
    avatar varchar(500) null comment '头像地址',
    password_hash varchar(100) not null comment '密码哈希',
    role varchar(32) not null comment '兼容角色标识：ADMIN系统管理员，USER普通用户，HR历史招聘人员',
    status varchar(32) not null comment '用户状态：ENABLED启用，DISABLED禁用',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    unique key uk_hr_user_email (email),
    unique key uk_hr_user_wecom_userid (wecom_userid),
    key idx_hr_user_mobile (mobile),
    key idx_hr_user_status (status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = 'HR用户表';

call add_column_if_missing('hr_user', 'mobile', 'alter table hr_user add column mobile varchar(32) null comment ''手机号'' after email');
call add_column_if_missing('hr_user', 'wecom_userid', 'alter table hr_user add column wecom_userid varchar(100) null comment ''企业微信成员UserID'' after mobile');
call add_column_if_missing('hr_user', 'avatar', 'alter table hr_user add column avatar varchar(500) null comment ''头像地址'' after wecom_userid');
call add_index_if_missing('hr_user', 'uk_hr_user_wecom_userid', 'alter table hr_user add unique key uk_hr_user_wecom_userid (wecom_userid)');
call add_index_if_missing('hr_user', 'idx_hr_user_mobile', 'create index idx_hr_user_mobile on hr_user (mobile)');

create table if not exists rbac_role (
    id bigint not null auto_increment comment '角色ID',
    code varchar(64) not null comment '角色编码',
    name varchar(100) not null comment '角色名称',
    description varchar(500) null comment '角色说明',
    status varchar(32) not null comment '角色状态：ENABLED启用，DISABLED禁用',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    unique key uk_rbac_role_code (code),
    key idx_rbac_role_status (status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = 'RBAC角色表';

create table if not exists rbac_permission (
    id bigint not null auto_increment comment '权限ID',
    parent_id bigint not null default 0 comment '父级权限ID，根节点为0',
    code varchar(100) not null comment '权限编码',
    permission_key varchar(100) null comment '前端权限标识',
    name varchar(100) not null comment '权限名称',
    type varchar(32) not null comment '权限类型：MENU菜单，BUTTON按钮，API接口',
    resource_path varchar(300) null comment '资源路径',
    component varchar(100) null comment '前端组件或页面标识',
    description varchar(500) null comment '权限说明',
    sort_no int not null comment '排序号',
    status varchar(32) not null comment '权限状态：ENABLED启用，DISABLED禁用',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    unique key uk_rbac_permission_code (code),
    key idx_rbac_permission_parent_id (parent_id),
    key idx_rbac_permission_type (type),
    key idx_rbac_permission_status (status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = 'RBAC权限表';

call add_column_if_missing('rbac_permission', 'parent_id', 'alter table rbac_permission add column parent_id bigint not null default 0 comment ''父级权限ID，根节点为0'' after id');
call add_column_if_missing('rbac_permission', 'permission_key', 'alter table rbac_permission add column permission_key varchar(100) null comment ''前端权限标识'' after code');
call add_column_if_missing('rbac_permission', 'component', 'alter table rbac_permission add column component varchar(100) null comment ''前端组件或页面标识'' after resource_path');
call add_index_if_missing('rbac_permission', 'idx_rbac_permission_parent_id', 'create index idx_rbac_permission_parent_id on rbac_permission (parent_id)');

create table if not exists rbac_user_role (
    id bigint not null auto_increment comment '用户角色关联ID',
    user_id bigint not null comment '用户ID',
    role_id bigint not null comment '角色ID',
    created_at datetime not null comment '创建时间',
    primary key (id),
    unique key uk_rbac_user_role_user_role (user_id, role_id),
    key idx_rbac_user_role_user_id (user_id),
    key idx_rbac_user_role_role_id (role_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = 'RBAC用户角色关联表';

create table if not exists rbac_role_permission (
    id bigint not null auto_increment comment '角色权限关联ID',
    role_id bigint not null comment '角色ID',
    permission_id bigint not null comment '权限ID',
    created_at datetime not null comment '创建时间',
    primary key (id),
    unique key uk_rbac_role_permission_role_permission (role_id, permission_id),
    key idx_rbac_role_permission_role_id (role_id),
    key idx_rbac_role_permission_permission_id (permission_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = 'RBAC角色权限关联表';

create table if not exists job_position (
    id bigint not null auto_increment comment '岗位ID',
    title varchar(200) not null comment '岗位名称',
    jd text not null comment '岗位JD',
    requirements text null comment '能力要求',
    status varchar(32) not null comment '岗位状态：ENABLED启用，DISABLED停用',
    created_by bigint not null comment '创建人ID',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    key idx_job_position_status (status),
    key idx_job_position_created_by (created_by),
    key idx_job_position_created_at (created_at),
    key idx_job_position_status_created_at (status, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '岗位表';

create table if not exists evaluation_dimension (
    id bigint not null auto_increment comment '评分维度ID',
    job_id bigint not null comment '岗位ID',
    name varchar(100) not null comment '维度名称',
    description text null comment '维度说明',
    weight decimal(10, 2) not null comment '维度权重',
    created_at datetime not null comment '创建时间',
    primary key (id),
    key idx_evaluation_dimension_job_id (job_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '岗位评分维度表';

create table if not exists candidate (
    id bigint not null auto_increment comment '候选人ID',
    job_id bigint not null comment '绑定岗位ID',
    name varchar(100) not null comment '候选人姓名',
    gender varchar(32) null comment '性别：MALE男，FEMALE女，UNKNOWN未知',
    age int null comment '年龄',
    phone varchar(32) null comment '手机号',
    email varchar(200) null comment '邮箱',
    resume_text text null comment '简历文本',
    resume_file_url varchar(500) null comment '简历文件地址',
    created_by bigint not null comment '创建人ID',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    key idx_candidate_job_id (job_id),
    key idx_candidate_name (name),
    key idx_candidate_phone (phone),
    key idx_candidate_email (email),
    key idx_candidate_created_by (created_by),
    key idx_candidate_created_at (created_at),
    key idx_candidate_job_created_at (job_id, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '候选人表';

call add_column_if_missing('candidate', 'job_id', 'alter table candidate add column job_id bigint null comment ''绑定岗位ID'' after id');
call add_column_if_missing('candidate', 'gender', 'alter table candidate add column gender varchar(32) null comment ''性别：MALE男，FEMALE女，UNKNOWN未知'' after name');
call add_column_if_missing('candidate', 'age', 'alter table candidate add column age int null comment ''年龄'' after gender');
call add_index_if_missing('candidate', 'idx_candidate_job_id', 'create index idx_candidate_job_id on candidate (job_id)');
call add_index_if_missing('candidate', 'idx_candidate_created_at', 'create index idx_candidate_created_at on candidate (created_at)');
call add_index_if_missing('candidate', 'idx_candidate_job_created_at', 'create index idx_candidate_job_created_at on candidate (job_id, created_at)');

create table if not exists interview_session (
    id bigint not null auto_increment comment '面试会话ID',
    job_id bigint not null comment '岗位ID',
    candidate_id bigint not null comment '候选人ID',
    status varchar(32) not null comment '面试状态：CREATED已创建，INVITED已邀请，WAITING等待中，IN_PROGRESS面试中，GENERATING报告生成中，COMPLETED已完成，FAILED失败，CANCELLED已取消，EXPIRED已过期',
    invite_token varchar(64) not null comment '面试邀请令牌',
    access_code_hash varchar(100) null comment '面试访问口令哈希',
    started_at datetime null comment '面试开始时间',
    ended_at datetime null comment '面试结束时间',
    fail_reason text null comment '失败原因',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    unique key uk_interview_session_invite_token (invite_token),
    key idx_interview_session_job_id (job_id),
    key idx_interview_session_candidate_id (candidate_id),
    key idx_interview_session_status (status),
    key idx_interview_session_created_at (created_at),
    key idx_interview_session_status_created_at (status, created_at),
    key idx_interview_session_job_created_at (job_id, created_at),
    key idx_interview_session_candidate_created_at (candidate_id, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '面试会话表';

call add_column_if_missing('interview_session', 'access_code_hash', 'alter table interview_session add column access_code_hash varchar(100) null comment ''面试访问口令哈希'' after invite_token');
call add_index_if_missing('interview_session', 'idx_interview_session_created_at', 'create index idx_interview_session_created_at on interview_session (created_at)');
call add_index_if_missing('interview_session', 'idx_interview_session_status_created_at', 'create index idx_interview_session_status_created_at on interview_session (status, created_at)');
call add_index_if_missing('interview_session', 'idx_interview_session_job_created_at', 'create index idx_interview_session_job_created_at on interview_session (job_id, created_at)');
call add_index_if_missing('interview_session', 'idx_interview_session_candidate_created_at', 'create index idx_interview_session_candidate_created_at on interview_session (candidate_id, created_at)');

create table if not exists interview_message (
    id bigint not null auto_increment comment '面试消息ID',
    session_id bigint not null comment '面试会话ID',
    role varchar(32) not null comment '消息角色：AI人工智能面试官，CANDIDATE候选人，SYSTEM系统',
    content text null comment '消息文本内容',
    audio_url varchar(500) null comment '音频文件地址',
    sequence_no int not null comment '消息顺序号',
    created_at datetime not null comment '创建时间',
    primary key (id),
    key idx_interview_message_session_id (session_id),
    key idx_interview_message_sequence_no (session_id, sequence_no),
    key idx_interview_message_session_sequence_id (session_id, sequence_no, id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '面试消息记录表';

call add_index_if_missing('interview_message', 'idx_interview_message_session_sequence_id', 'create index idx_interview_message_session_sequence_id on interview_message (session_id, sequence_no, id)');

create table if not exists interview_report (
    id bigint not null auto_increment comment '面试报告ID',
    session_id bigint not null comment '面试会话ID',
    total_score decimal(10, 2) not null comment '总分',
    dimension_scores_json text null comment '各维度评分JSON',
    strengths text null comment '候选人优势',
    risks text null comment '候选人风险点',
    recommendation varchar(32) not null comment '推荐结果：RECOMMEND推荐，HOLD待定，REJECT不推荐',
    follow_up_questions text null comment '推荐追问问题JSON',
    raw_report_json text null comment '原始报告JSON',
    created_at datetime not null comment '创建时间',
    primary key (id),
    unique key uk_interview_report_session_id (session_id),
    key idx_interview_report_created_at (created_at),
    key idx_interview_report_recommendation_created_at (recommendation, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '面试评估报告表';

call add_index_if_missing('job_position', 'idx_job_position_created_at', 'create index idx_job_position_created_at on job_position (created_at)');
call add_index_if_missing('job_position', 'idx_job_position_status_created_at', 'create index idx_job_position_status_created_at on job_position (status, created_at)');
call add_index_if_missing('interview_report', 'idx_interview_report_created_at', 'create index idx_interview_report_created_at on interview_report (created_at)');
call add_index_if_missing('interview_report', 'idx_interview_report_recommendation_created_at', 'create index idx_interview_report_recommendation_created_at on interview_report (recommendation, created_at)');

create table if not exists ai_interview_setting (
    id bigint not null comment '配置ID，全局配置固定为1',
    target_question_count int not null comment '目标提问数量，AI接近该数量后开始收尾',
    max_question_count int not null comment '最大提问数量，达到后停止追问',
    closing_follow_up_turn_limit int not null default 1 comment '达到最大提问数后允许候选人补充说明或反问的轮次数',
    max_follow_up_per_topic int not null comment '同一能力点或同一项目连续追问上限',
    min_effective_answer_count int not null comment '最低有效回答轮次',
    insufficient_answer_max_score int not null comment '有效回答轮次不足时最高总分',
    no_evidence_max_score int not null comment '没有真实案例或细节时最高总分',
    weak_job_match_max_score int not null comment '岗位匹配度较弱时最高总分',
    weak_answer_max_score int not null comment '回答有效性明显不足时最高总分',
    candidate_question_answer_guide text null comment '候选人反问回答口径',
    updated_at datetime not null comment '更新时间',
    primary key (id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = 'AI面试边界配置表';

call add_column_if_missing('ai_interview_setting', 'candidate_question_answer_guide', 'alter table ai_interview_setting add column candidate_question_answer_guide text null comment ''候选人反问回答口径'' after weak_answer_max_score');
call add_column_if_missing('ai_interview_setting', 'closing_follow_up_turn_limit', 'alter table ai_interview_setting add column closing_follow_up_turn_limit int not null default 1 comment ''达到最大提问数后允许候选人补充说明或反问的轮次数'' after max_question_count');

insert into ai_interview_setting (
    id,
    target_question_count,
    max_question_count,
    closing_follow_up_turn_limit,
    max_follow_up_per_topic,
    min_effective_answer_count,
    insufficient_answer_max_score,
    no_evidence_max_score,
    weak_job_match_max_score,
    weak_answer_max_score,
    candidate_question_answer_guide,
    updated_at
)
select 1, 8, 12, 1, 2, 2, 60, 70, 74, 59, '试岗期/试岗：7天
作息时间/上下班/大小周/休息时间：大小周，9:30 到 18:00
工资发放时间/发薪日/几号发工资：每月18号
薪资结构/工资/底薪/提成/业绩/奖金/薪酬：由线下面试 HR 进一步沟通确认，不回答金额、比例或规则
试用期/福利/调休/加班/社保/公积金：以 HR 后续正式沟通为准', now()
where not exists (select 1 from ai_interview_setting where id = 1);

update ai_interview_setting
set closing_follow_up_turn_limit = 1
where id = 1
  and closing_follow_up_turn_limit is null;

update ai_interview_setting
set candidate_question_answer_guide = '试岗期/试岗：7天
作息时间/上下班/大小周/休息时间：大小周，9:30 到 18:00
工资发放时间/发薪日/几号发工资：每月18号
薪资结构/工资/底薪/提成/业绩/奖金/薪酬：由线下面试 HR 进一步沟通确认，不回答金额、比例或规则
试用期/福利/调休/加班/社保/公积金：以 HR 后续正式沟通为准'
where id = 1
  and (candidate_question_answer_guide is null or candidate_question_answer_guide = '');

-- 初始化管理员账号示例：
-- 密码请使用 BCrypt 生成后写入 password_hash，不要存储明文密码。
-- insert into hr_user (name, email, password_hash, role, status, created_at, updated_at)
-- values ('管理员', 'admin@example.com', '$2a$10$replace_with_bcrypt_hash', 'ADMIN', 'ENABLED', now(), now());

-- RBAC初始化角色：
insert into rbac_role (code, name, description, status, created_at, updated_at)
select 'ADMIN', '管理员', '拥有系统全部权限', 'ENABLED', now(), now()
where not exists (select 1 from rbac_role where code = 'ADMIN');

insert into rbac_role (code, name, description, status, created_at, updated_at)
select 'HR', '招聘人员', '拥有招聘业务常用操作权限', 'ENABLED', now(), now()
where not exists (select 1 from rbac_role where code = 'HR');

-- 登录态基础接口（查询当前用户、退出登录、修改本人密码）不进入RBAC权限配置。

-- RBAC初始化前端菜单和按钮权限：
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select 0, 'menu:jobs', 'jobs', '岗位管理', 'MENU', null, 'jobs', '岗位管理菜单', 1000, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'menu:jobs');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select 0, 'menu:candidates', 'candidates', '候选人管理', 'MENU', null, 'candidates', '候选人管理菜单', 2000, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'menu:candidates');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select 0, 'menu:interviews', 'interviews', '面试管理', 'MENU', null, 'interviews', '面试管理菜单', 3000, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'menu:interviews');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select 0, 'menu:aiSettings', 'aiSettings', 'AI面试配置', 'MENU', null, 'aiSettings', 'AI提问边界和评分边界配置菜单', 4000, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'menu:aiSettings');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select 0, 'menu:rbac', 'rbac', '权限管理', 'MENU', null, 'rbac', '权限管理父菜单', 9000, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'menu:rbac');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select parent.id, 'menu:rbac:menus', 'rbacMenus', '菜单管理', 'MENU', null, 'rbacMenus', '菜单、按钮和接口权限管理', 9010, 'ENABLED', now(), now()
from rbac_permission parent
where parent.code = 'menu:rbac'
  and not exists (select 1 from rbac_permission where code = 'menu:rbac:menus');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select parent.id, 'menu:rbac:roles', 'rbacRoles', '角色管理', 'MENU', null, 'rbacRoles', '角色和角色权限管理', 9020, 'ENABLED', now(), now()
from rbac_permission parent
where parent.code = 'menu:rbac'
  and not exists (select 1 from rbac_permission where code = 'menu:rbac:roles');
insert into rbac_permission (parent_id, code, permission_key, name, type, resource_path, component, description, sort_no, status, created_at, updated_at)
select parent.id, 'menu:rbac:users', 'rbacUsers', '用户管理', 'MENU', null, 'rbacUsers', '用户角色授权管理', 9030, 'ENABLED', now(), now()
from rbac_permission parent
where parent.code = 'menu:rbac'
  and not exists (select 1 from rbac_permission where code = 'menu:rbac:users');

insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'job:create', '创建岗位', 'API', '/api/jobs/create', '创建岗位', 200, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'job:create');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'job:update', '更新岗位', 'API', '/api/jobs/update', '更新岗位', 210, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'job:update');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'job:delete', '删除岗位', 'API', '/api/jobs/delete', '删除岗位', 220, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'job:delete');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'job:detail', '查询岗位详情', 'API', '/api/jobs/detail', '查询岗位详情', 230, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'job:detail');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'job:list', '查询岗位列表', 'API', '/api/jobs/list', '查询岗位列表', 240, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'job:list');

insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'candidate:create', '创建候选人', 'API', '/api/candidates/create', '创建候选人', 300, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'candidate:create');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'candidate:update', '更新候选人', 'API', '/api/candidates/update', '更新候选人', 310, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'candidate:update');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'candidate:delete', '删除候选人', 'API', '/api/candidates/delete', '删除候选人', 320, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'candidate:delete');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'candidate:detail', '查询候选人详情', 'API', '/api/candidates/detail', '查询候选人详情', 330, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'candidate:detail');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'candidate:list', '查询候选人列表', 'API', '/api/candidates/list', '查询候选人列表', 340, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'candidate:list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'candidate:resume-parse', '解析PDF简历', 'API', '/api/candidates/resume/parse-pdf', '解析PDF简历', 350, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'candidate:resume-parse');

insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:create', '创建面试会话', 'API', '/api/interviews/create', '创建面试会话', 400, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:create');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:detail', '查询面试详情', 'API', '/api/interviews/detail', '查询面试会话详情', 410, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:detail');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:list', '查询面试列表', 'API', '/api/interviews/list', '查询面试会话列表', 420, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:access-code-reset', '重置面试口令', 'API', '/api/interviews/access-code/reset', '重置面试访问口令', 430, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:access-code-reset');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:finish', '结束面试', 'API', '/api/interviews/finish', '后台结束面试并触发报告生成', 440, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:finish');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:messages-list', '查询面试消息', 'API', '/api/interviews/messages/list', '查询面试消息记录', 450, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:messages-list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:report-detail', '查询面试报告', 'API', '/api/interviews/reports/detail', '查询面试评估报告', 460, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:report-detail');

insert into rbac_permission (parent_id, code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select parent.id, 'ai-setting:detail', '查询AI面试配置', 'API', '/api/settings/ai-interview/detail', '查询AI提问边界和评分边界配置', 460, 'ENABLED', now(), now()
from rbac_permission parent
where parent.code = 'menu:aiSettings'
  and not exists (select 1 from rbac_permission where code = 'ai-setting:detail');
insert into rbac_permission (parent_id, code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select parent.id, 'ai-setting:update', '更新AI面试配置', 'API', '/api/settings/ai-interview/update', '更新AI提问边界和评分边界配置', 470, 'ENABLED', now(), now()
from rbac_permission parent
where parent.code = 'menu:aiSettings'
  and not exists (select 1 from rbac_permission where code = 'ai-setting:update');

insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:role:list', '查询角色列表', 'API', '/api/rbac/roles/list', '查询RBAC角色列表', 500, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:role:list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:role:create', '创建角色', 'API', '/api/rbac/roles/create', '创建RBAC角色', 510, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:role:create');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:role:update', '更新角色', 'API', '/api/rbac/roles/update', '更新RBAC角色', 520, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:role:update');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:role:delete', '删除角色', 'API', '/api/rbac/roles/delete', '删除RBAC角色', 530, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:role:delete');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:permission:list', '查询权限列表', 'API', '/api/rbac/permissions/list', '查询RBAC权限列表', 540, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:permission:list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:permission:create', '创建菜单权限', 'API', '/api/rbac/permissions/create', '创建菜单、按钮或接口权限', 541, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:permission:create');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:permission:update', '更新菜单权限', 'API', '/api/rbac/permissions/update', '更新菜单、按钮或接口权限', 542, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:permission:update');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:permission:delete', '删除菜单权限', 'API', '/api/rbac/permissions/delete', '删除菜单、按钮或接口权限', 543, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:permission:delete');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:role-permission:detail', '查询角色权限', 'API', '/api/rbac/roles/permissions/detail', '查询角色已绑定权限', 550, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:role-permission:detail');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:role-permission:save', '保存角色权限', 'API', '/api/rbac/roles/permissions/save', '保存角色权限绑定', 560, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:role-permission:save');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:user:list', '查询授权用户', 'API', '/api/rbac/users/list', '查询用户角色授权列表', 570, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:user:list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:user:create', '创建用户', 'API', '/api/rbac/users/create', '创建后台用户', 580, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:user:create');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:user-password:reset', '重置用户密码', 'API', '/api/rbac/users/password/reset', '重置后台用户登录密码', 590, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:user-password:reset');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'rbac:user-role:save', '保存用户角色', 'API', '/api/rbac/users/roles/save', '保存用户角色绑定', 600, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'rbac:user-role:save');

-- 将接口和按钮权限挂载到对应菜单下，便于角色授权按树查看。
update rbac_permission child
join rbac_permission parent on parent.code = 'menu:jobs'
set child.parent_id = parent.id
where child.code in ('job:create', 'job:update', 'job:delete', 'job:detail', 'job:list');

update rbac_permission child
join rbac_permission parent on parent.code = 'menu:candidates'
set child.parent_id = parent.id
where child.code in ('candidate:create', 'candidate:update', 'candidate:delete', 'candidate:detail', 'candidate:list', 'candidate:resume-parse');

update rbac_permission child
join rbac_permission parent on parent.code = 'menu:interviews'
set child.parent_id = parent.id
where child.code in ('interview:create', 'interview:detail', 'interview:list', 'interview:access-code-reset', 'interview:finish', 'interview:messages-list', 'interview:report-detail');

update rbac_permission child
join rbac_permission parent on parent.code = 'menu:aiSettings'
set child.parent_id = parent.id
where child.code in ('ai-setting:detail', 'ai-setting:update');

update rbac_permission child
join rbac_permission parent on parent.code = 'menu:rbac:menus'
set child.parent_id = parent.id
where child.code in ('rbac:permission:list', 'rbac:permission:create', 'rbac:permission:update', 'rbac:permission:delete');

update rbac_permission child
join rbac_permission parent on parent.code = 'menu:rbac:roles'
set child.parent_id = parent.id
where child.code in ('rbac:role:list', 'rbac:role:create', 'rbac:role:update', 'rbac:role:delete', 'rbac:role-permission:detail', 'rbac:role-permission:save');

update rbac_permission child
join rbac_permission parent on parent.code = 'menu:rbac:users'
set child.parent_id = parent.id
where child.code in ('rbac:user:list', 'rbac:user:create', 'rbac:user-password:reset', 'rbac:user-role:save');

-- ADMIN拥有全部权限；HR拥有当前招聘业务全部权限。后续可按角色移除不需要的权限。
insert ignore into rbac_role_permission (role_id, permission_id, created_at)
select r.id, p.id, now()
from rbac_role r
join rbac_permission p
where r.code in ('ADMIN', 'HR');

-- 将旧用户表中的role字段迁移为RBAC用户角色关系。
insert ignore into rbac_user_role (user_id, role_id, created_at)
select u.id, r.id, now()
from hr_user u
join rbac_role r on r.code = u.role;

drop procedure if exists add_column_if_missing;
drop procedure if exists add_index_if_missing;
