package com.orhoj.trackunit.demo.config;

import com.orhoj.demo.hiremanager.api.AccountsApi;
import com.orhoj.demo.hiremanager.api.HiresApi;
import com.orhoj.demo.hiremanager.api.MachinesApi;
import com.orhoj.demo.hiremanager.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HireManagerConfig {

    @Bean
    public ApiClient hireManagerApiClient(
            @Value("${hire-manager.base-url}") String baseUrl,
            @Value("${hire-manager.username}") String username,
            @Value("${hire-manager.password}") String password) {

        RestClient restClient = ApiClient.buildRestClientBuilder()
                .baseUrl(baseUrl)
                .requestInterceptor(new HireManagerTokenInterceptor(baseUrl, username, password))
                .build();

        ApiClient apiClient = new ApiClient(restClient);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public HiresApi hiresApi(ApiClient hireManagerApiClient) {
        return new HiresApi(hireManagerApiClient);
    }

    @Bean
    public MachinesApi machinesApi(ApiClient hireManagerApiClient) {
        return new MachinesApi(hireManagerApiClient);
    }

    @Bean
    public AccountsApi accountsApi(ApiClient hireManagerApiClient) {
        return new AccountsApi(hireManagerApiClient);
    }
}
