package net.acesinc.convergentui.content;

import java.io.Serializable;

import org.springframework.http.MediaType;

public class ContentResponse implements Serializable {
	
	private static final long serialVersionUID = -1641266191562236026L;
	
	private Object content;
	
	private MediaType contentType;
	
	private boolean error = false;
	
	private String message;
	
	@Override
	public String toString() {
		if (this.error) {
			return "Content has ERROR: " + this.message + " \nContent: " + this.content;
		}
		return "Content: " + this.content;
	}
	
	public Object getContent() {
		return this.content;
	}
	
	public void setContent(final Object content) {
		this.content = content;
	}
	
	public boolean isError() {
		return this.error;
	}
	
	public void setError(final boolean error) {
		this.error = error;
	}
	
	public String getMessage() {
		return this.message;
	}
	
	public void setMessage(final String message) {
		this.message = message;
	}
	
	public MediaType getContentType() {
		return this.contentType;
	}
	
	public void setContentType(final MediaType contentType) {
		this.contentType = contentType;
	}
}
