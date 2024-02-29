package com.myForum.service.impl;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Resource;

import com.myForum.entity.dto.SysSettingDto;
import com.myForum.entity.enums.SysSettingCodeEnum;
import com.myForum.exception.BusinessException;
import com.myForum.utils.JsonUtils;
import com.myForum.utils.SysCacheUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.myForum.entity.enums.PageSize;
import com.myForum.entity.query.SysSettingQuery;
import com.myForum.entity.po.SysSetting;
import com.myForum.entity.vo.PaginationResultVO;
import com.myForum.entity.query.SimplePage;
import com.myForum.mappers.SysSettingMapper;
import com.myForum.service.SysSettingService;
import com.myForum.utils.StringTools;


/**
 * 系统设置信息 业务接口实现
 */
@Service("sysSettingService")
public class SysSettingServiceImpl implements SysSettingService {

	private static final Logger logger = LoggerFactory.getLogger(SysSettingServiceImpl.class);

	@Resource
	private SysSettingMapper<SysSetting, SysSettingQuery> sysSettingMapper;

	@Resource
	@Lazy
	private SysSettingService sysSettingService;

	/**
	 * 刷新缓存
	 *
	 * @return
	 */
	@Override
	public SysSettingDto refreshCache() {
		try {
			SysSettingDto sysSettingDto = new SysSettingDto();
			List<SysSetting> list = this.sysSettingMapper.selectList(new SysSettingQuery());
			for(SysSetting sysSetting : list) {
				String jsonContent = sysSetting.getJsonContent();
				if(jsonContent == null) {
					continue;
				}
				String code = sysSetting.getCode();
				SysSettingCodeEnum sysSettingCodeEnum = SysSettingCodeEnum.getByCode(code);
				PropertyDescriptor pd = new PropertyDescriptor(sysSettingCodeEnum.getPropName(), SysSettingDto.class);
				Method method = pd.getWriteMethod();
				Class classZ = Class.forName(sysSettingCodeEnum.getClassZ());
				method.invoke(sysSettingDto, JsonUtils.convertJson2Obj(jsonContent, classZ));
			}
			SysCacheUtils.refresh(sysSettingDto);
			return sysSettingDto;
		} catch (Exception e) {
			logger.error("刷新缓存失败", e);
			throw new BusinessException("刷新缓存失败");
		}
	}

	/**
	 * 修改并保存系统设置
	 *
	 * @param sysSettingDto
	 */
	@Override
	public void saveSetting(SysSettingDto sysSettingDto) {
		try {
			Class classz = SysSettingDto.class;
			for (SysSettingCodeEnum codeEnum : SysSettingCodeEnum.values()) {
				PropertyDescriptor pd = new PropertyDescriptor(codeEnum.getPropName(), classz);
				Method method = pd.getReadMethod();
				Object obj = method.invoke(sysSettingDto);
				SysSetting setting = new SysSetting();
				setting.setCode(codeEnum.getCode());
				setting.setJsonContent(JsonUtils.convertObj2Json(obj));
				this.sysSettingMapper.insertOrUpdate(setting);
				sysSettingService.refreshCache();
			}
		} catch (Exception e) {
			logger.error("保存设置失败", e);
			throw new BusinessException("保存设置失败");
		}
	}
}