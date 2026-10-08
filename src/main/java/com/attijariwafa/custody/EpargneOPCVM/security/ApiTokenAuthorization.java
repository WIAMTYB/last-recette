package com.attijariwafa.custody.EpargneOPCVM.security;

public record ApiTokenAuthorization(String pathPattern, String httpMethod) {

    public ApiTokenAuthorization {
        pathPattern = pathPattern == null ? null : pathPattern.trim();
        httpMethod = httpMethod == null ? null : httpMethod.trim();
    }

    public boolean matchesMethod(String requestMethod) {
        return httpMethod == null || httpMethod.isBlank() || httpMethod.equalsIgnoreCase(requestMethod);
    }
}
