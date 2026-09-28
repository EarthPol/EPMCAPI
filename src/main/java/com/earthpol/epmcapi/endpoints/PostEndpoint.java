package com.earthpol.epmcapi.endpoints;

import com.google.gson.JsonElement;
import com.earthpol.epmcapi.networking.FieldSelection;

public abstract class PostEndpoint<T> {
    // Parse the request JSON payload and return a model object
    public abstract T getObjectOrNull(JsonElement element);

    // Convert your model object to a JSON response
    public abstract JsonElement getJsonElement(T object);

    public JsonElement getJsonElement(T object, FieldSelection fields) {
        return fields.project(getJsonElement(object));
    }
}
