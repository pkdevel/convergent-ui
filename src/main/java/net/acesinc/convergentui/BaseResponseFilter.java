/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui;

import com.netflix.zuul.context.RequestContext;

/**
 * @author andrewserff
 */
public abstract class BaseResponseFilter extends BaseFilter {
	
	@Override
	public String filterType() {
		return "post";
	}
	
	@Override
	public int filterOrder() {
		return 1; // run before any others
	}
	
	@Override
	public boolean shouldFilter() {
		final RequestContext ctx = RequestContext.getCurrentContext();
		final String contentType = getContentType(ctx);
		final String verb = getVerb(ctx.getRequest());
		
		return "text/html".equals(contentType)
				&& "GET".equalsIgnoreCase(verb)
				&&
				(!ctx.getZuulResponseHeaders().isEmpty()
						|| ctx.getResponseDataStream() != null
						|| ctx.getResponseBody() != null);
	}
}
