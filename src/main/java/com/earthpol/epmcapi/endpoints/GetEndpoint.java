package com.earthpol.epmcapi.endpoints;

import com.google.gson.JsonElement;

public abstract class GetEndpoint {
    // Called when a GET request should be processed
    public abstract String lookup();

    // Optionally, provide a utility method that returns a JSON element
    public abstract JsonElement getJsonElement();
}