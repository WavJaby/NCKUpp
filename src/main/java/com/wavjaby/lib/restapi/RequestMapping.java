package com.wavjaby.lib.restapi;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

@Target({METHOD, TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {
    String[] value() default "";

    RequestMethod method() default RequestMethod.GET;
}
