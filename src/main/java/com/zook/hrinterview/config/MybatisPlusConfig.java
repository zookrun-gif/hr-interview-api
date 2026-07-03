package com.zook.hrinterview.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        fillIfFieldExists(metaObject, "createdAt", now);
        fillIfFieldExists(metaObject, "updatedAt", now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfFieldExists(metaObject, "updatedAt", LocalDateTime.now());
    }

    private void fillIfFieldExists(MetaObject metaObject, String fieldName, LocalDateTime value) {
        if (metaObject.hasGetter(fieldName) && metaObject.hasSetter(fieldName)) {
            strictFillStrategy(metaObject, fieldName, () -> value);
        }
    }
}
