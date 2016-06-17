package org.ibase4j.service.sys;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ibase4j.core.base.BaseService;
import org.ibase4j.core.support.SysEventService;
import org.ibase4j.core.support.dubbo.spring.annotation.DubboReference;
import org.ibase4j.core.util.DateUtil;
import org.ibase4j.core.util.ExceptionUtil;
import org.ibase4j.core.util.InstanceUtil;
import org.ibase4j.core.util.WebUtil;
import org.ibase4j.model.generator.SysEvent;
import org.ibase4j.model.generator.SysPermission;
import org.ibase4j.provider.sys.SysEventProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageInfo;

@Service
public class SysEventServiceImpl extends BaseService<SysEventProvider, SysEvent> implements SysEventService {
	@DubboReference
	public void setProvider(SysEventProvider provider) {
		this.provider = provider;
	}

	@Autowired
	private SysPermissionService sysPermissionService;
	private ExecutorService executorService = Executors.newCachedThreadPool();

	public void saveEvent(final HttpServletRequest request, final HttpServletResponse response, final Exception ex,
			final Long startTime, final Long endTime) {
		final SysEvent record = new SysEvent();
		record.setMethod(request.getMethod());
		record.setRequestUri(request.getServletPath());
		record.setClientHost(WebUtil.getHost(request));
		record.setUserAgent(request.getHeader("user-agent"));
		record.setParammeters(JSON.toJSONString(request.getParameterMap()));
		record.setCreateBy(WebUtil.getCurrentUser());
		record.setStatus(response.getStatus());

		executorService.submit(new Runnable() {
			public void run() {
				try { // 保存操作
					record.setRemark(ExceptionUtil.getStackTraceAsString(ex));
					Map<String, Object> params = InstanceUtil.newHashMap();
					params.put("permission_url", record.getRequestUri());
					PageInfo<SysPermission> pageInfo = sysPermissionService.query(params);
					if (pageInfo.getSize() > 0) {
						record.setTitle(pageInfo.getList().get(0).getPermissionName());
					}
					add(record);
					// 内存信息
					if (logger.isDebugEnabled()) {
						String message = "开始时间: {}; 结束时间: {}; 耗时: {}s; URI: {}; 最大内存: {}M; 已分配内存: {}M; 已分配内存中的剩余空间: {}M; 最大可用内存: {}M.";
						long total = Runtime.getRuntime().totalMemory() / 1024 / 1024;
						long max = Runtime.getRuntime().maxMemory() / 1024 / 1024;
						long free = Runtime.getRuntime().freeMemory() / 1024 / 1024;
						logger.debug(message, DateUtil.format(startTime, "hh:mm:ss.SSS"),
								DateUtil.format(endTime, "hh:mm:ss.SSS"), (endTime - startTime) / 1000.00,
								record.getRequestUri(), max, total, free, max - total + free);
					}
				} catch (Exception e) {
					logger.error("Save event log cause error :", e);
				}
			}
		});
	}
}