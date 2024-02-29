package com.myForum.annotation;

import com.myForum.entity.enums.UserOperaFrequencyTypeEnum;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GlobalInterceptor {

    /**
     * 是否需要登录
     */
    boolean checkLogin() default false;

    /**
     * 是否需要校验参数
     */
    boolean checkParams() default false;

    /**
     * 校验频次
     */
    UserOperaFrequencyTypeEnum frequencyType() default UserOperaFrequencyTypeEnum.NO_CHECK;
}
