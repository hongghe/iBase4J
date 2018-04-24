package org.ibase4j.service.sys;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ibase4j.mapper.sys.SysUserMapper;
import org.ibase4j.mapper.sys.SysUserThirdpartyMapper;
import org.ibase4j.model.sys.SysDept;
import org.ibase4j.model.sys.SysUser;
import org.ibase4j.model.sys.SysUserThirdparty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import top.ibase4j.core.base.BaseService;
import top.ibase4j.core.support.Pagination;
import top.ibase4j.core.support.login.ThirdPartyUser;
import top.ibase4j.core.util.InstanceUtil;
import top.ibase4j.core.util.SecurityUtil;

/**
 * SysUser服务实现类
 * 
 * @author ShenHuaJie
 * @version 2016-08-27 22:39:42
 */
@Service
@CacheConfig(cacheNames = "sysUser")
public class SysUserService extends BaseService<SysUser, SysUserMapper> {
	@Autowired
	private SysUserThirdpartyMapper thirdpartyMapper;
	@Autowired
	private SysDicService sysDicService;
	@Autowired
	private SysDeptService sysDeptService;
	@Autowired
	private SysAuthorizeService sysAuthorizeService;
	
	public SysUser queryById(Long id) {
		SysUser sysUser = super.queryById(id);
		if (sysUser != null) {
			if (sysUser.getDeptId() != null) {
				SysDept sysDept = sysDeptService.queryById(sysUser.getDeptId());
				if (sysDept != null) {
					sysUser.setDeptName(sysDept.getDeptName());
				} else {
					sysUser.setDeptId(null);
				}
			}
		}
		return sysUser;
	}
	
	public Pagination<SysUser> query(Map<String, Object> params) {
		Map<String, String> userTypeMap = sysDicService.queryDicByType("USERTYPE");
		Pagination<SysUser> pageInfo = super.query(params);
		for (SysUser userBean : pageInfo.getRecords()) {
		    userBean.setPassword(null);
			if (userBean.getUserType() != null) {
				userBean.setUserTypeText(userTypeMap.get(userBean.getUserType()));
			}
			List<String> permissions = sysAuthorizeService.queryUserPermission(userBean.getId());
			for (String permission : permissions) {
				if (StringUtils.isBlank(userBean.getPermission())) {
					userBean.setPermission(permission);
				} else {
					userBean.setPermission(userBean.getPermission() + ";" + permission);
				}
			}
		}
		return pageInfo;
	}

	/** 查询第三方帐号用户Id */
	@Cacheable
	public Long queryUserIdByThirdParty(ThirdPartyUser param) {
		return thirdpartyMapper.queryUserIdByThirdParty(param.getProvider(), param.getOpenid());
	}

	/** 保存第三方帐号 */
	@Transactional
	public SysUser insertThirdPartyUser(ThirdPartyUser thirdPartyUser) {
		SysUser sysUser = new SysUser();
		sysUser.setSex(0);
		sysUser.setUserType("1");
		sysUser.setPassword(SecurityUtil.encryptPassword("123456"));
		sysUser.setUserName(thirdPartyUser.getUserName());
		sysUser.setAvatar(thirdPartyUser.getAvatarUrl());
		// 初始化第三方信息
		SysUserThirdparty thirdparty = new SysUserThirdparty();
		thirdparty.setProvider(thirdPartyUser.getProvider());
		thirdparty.setOpenId(thirdPartyUser.getOpenid());
		thirdparty.setCreateTime(new Date());

		this.update(sysUser);
		sysUser.setAccount(thirdparty.getProvider() + sysUser.getId());
		this.update(sysUser);
		thirdparty.setUserId(sysUser.getId());
		thirdpartyMapper.insert(thirdparty);
		return sysUser;
	}

	public void init() {
	    queryList(InstanceUtil.newHashMap());
	}
}