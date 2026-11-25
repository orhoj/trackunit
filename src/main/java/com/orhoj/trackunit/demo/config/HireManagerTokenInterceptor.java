package com.orhoj.trackunit.demo.config;

import com.orhoj.demo.hiremanager.api.AuthenticationApi;
import com.orhoj.demo.hiremanager.invoker.ApiClient;
import com.orhoj.demo.hiremanager.model.LoginRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

@NullMarked
public class HireManagerTokenInterceptor implements ClientHttpRequestInterceptor {

    private final AuthenticationApi authApi;
    private final String username;
    private final String password;

    public HireManagerTokenInterceptor(String baseUrl, String username, String password) {
        ApiClient authClient = new ApiClient();
        authClient.setBasePath(baseUrl);
        this.authApi = new AuthenticationApi(authClient);
        this.username = username;
        this.password = password;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        // =========================================================================
        // TODO: Cache this token.
        // Currently logging in before EVERY request to keep things simple for MVP.
        // =========================================================================
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        String token = authApi.login(loginRequest);
        request.getHeaders().setBearerAuth(token);

        return execution.execute(request, body);
    }
}
