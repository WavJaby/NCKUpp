package com.wavjaby.lib.restapi.request;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;

@Target(PARAMETER)
public @interface PathVariable {
    String[] value();
}
