/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui;

import javax.servlet.http.HttpServletRequest;

import com.netflix.zuul.context.RequestContext;

/**
 * @author andrewserff
 */
public abstract class BaseRequestFilter extends BaseFilter {
	
	@Override
	public String filterType() {
		return "pre";
	}
	
	@Override
	public int filterOrder() {
		return 1;
	}
	
	@Override
	public boolean shouldFilter() {
		final HttpServletRequest req = RequestContext.getCurrentContext().getRequest();
		return "GET".equalsIgnoreCase(getVerb(req)) && isConvergentUIRequest(req);
	}
}
