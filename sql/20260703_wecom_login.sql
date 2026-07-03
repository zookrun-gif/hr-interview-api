-- 企业微信扫码登录字段扩展 SQL
-- 数据库：MySQL 8.x
-- 说明：该脚本不由程序自动执行，请在目标数据库中手动执行。

use hr_interview;

alter table hr_user
    add column mobile varchar(32) null comment '手机号' after email,
    add column wecom_userid varchar(100) null comment '企业微信成员UserID' after mobile,
    add column avatar varchar(500) null comment '头像地址' after wecom_userid,
    add unique key uk_hr_user_wecom_userid (wecom_userid),
    add key idx_hr_user_mobile (mobile);
