-- AI 面试初筛 MVP 初始化 SQL
-- 数据库：MySQL 8.x
-- 说明：该脚本不由程序自动执行，请在目标数据库中手动执行。

create database if not exists hr_interview
    default character set utf8mb4
    collate utf8mb4_0900_ai_ci;

use hr_interview;

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

-- 如果已执行过旧版RBAC建表SQL，请手动执行以下语句升级权限表结构：
-- alter table rbac_permission add column parent_id bigint not null default 0 comment '父级权限ID，根节点为0' after id;
-- alter table rbac_permission add column permission_key varchar(100) null comment '前端权限标识' after code;
-- alter table rbac_permission add column component varchar(100) null comment '前端组件或页面标识' after resource_path;
-- alter table rbac_permission add index idx_rbac_permission_parent_id (parent_id);
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
    key idx_job_position_created_by (created_by)
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
    key idx_candidate_created_by (created_by)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '候选人表';

-- 如果已执行过旧版建表 SQL，请手动执行以下语句为候选人表增加岗位绑定字段：
-- alter table candidate add column job_id bigint null comment '绑定岗位ID' after id;
-- update candidate set job_id = 1 where job_id is null;
-- alter table candidate modify column job_id bigint not null comment '绑定岗位ID';
-- alter table candidate add index idx_candidate_job_id (job_id);

-- 如果已执行过旧版建表 SQL，请手动执行以下语句为候选人表增加性别和年龄字段：
-- alter table candidate add column gender varchar(32) null comment '性别：MALE男，FEMALE女，UNKNOWN未知' after name;
-- alter table candidate add column age int null comment '年龄' after gender;

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
    key idx_interview_session_status (status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '面试会话表';

-- 如果已执行过旧版建表 SQL，请手动执行以下语句为面试会话表增加访问口令哈希字段：
-- alter table interview_session add column access_code_hash varchar(100) null comment '面试访问口令哈希' after invite_token;

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
    key idx_interview_message_sequence_no (session_id, sequence_no)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '面试消息记录表';


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
    unique key uk_interview_report_session_id (session_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci
  comment = '面试评估报告表';

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
select 'interview:messages-list', '查询面试消息', 'API', '/api/interviews/messages/list', '查询面试消息记录', 440, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:messages-list');
insert into rbac_permission (code, name, type, resource_path, description, sort_no, status, created_at, updated_at)
select 'interview:report-detail', '查询面试报告', 'API', '/api/interviews/reports/detail', '查询面试评估报告', 450, 'ENABLED', now(), now()
where not exists (select 1 from rbac_permission where code = 'interview:report-detail');

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
where child.code in ('interview:create', 'interview:detail', 'interview:list', 'interview:access-code-reset', 'interview:messages-list', 'interview:report-detail');

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
