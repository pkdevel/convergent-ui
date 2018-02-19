package net.acesinc.convergentui;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ConvergentUIConfiguration {
	
	@Bean
	@LoadBalanced
	public static RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
