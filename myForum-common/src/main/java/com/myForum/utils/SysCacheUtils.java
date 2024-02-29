package com.myForum.utils;

import com.myForum.entity.dto.SysSettingDto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统设置缓存刷新工具
 */
public class SysCacheUtils {

    private static final String KEY_SYS = "sys_setting";

    private static final Map<String, SysSettingDto> CACHE_DATA = new ConcurrentHashMap<>();

    public static SysSettingDto getSysSetting() {
        return CACHE_DATA.get(KEY_SYS);
    }

    public static void refresh(SysSettingDto dto) {
        CACHE_DATA.put(KEY_SYS, dto);
    }
}
