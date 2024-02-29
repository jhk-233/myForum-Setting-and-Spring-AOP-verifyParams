package com.myForum.annotation;

import com.myForum.entity.enums.VerifyRegexEnum;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER,ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface VerifyParam {

    /**
     * 是否需要校验
     */
    boolean required() default false;

    /**
     * 最大值
     */
    int max() default -1;

    /**
     * 最小值
     */
    int min() default -1;

    /**
     * 正则校验
     */
    VerifyRegexEnum regex() default VerifyRegexEnum.NO;

}
