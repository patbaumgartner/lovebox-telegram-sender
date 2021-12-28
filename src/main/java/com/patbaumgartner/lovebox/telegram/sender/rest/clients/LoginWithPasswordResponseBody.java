package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginWithPasswordResponseBody(@JsonProperty("_id") String id, String firstName,
                                            String email, String token) {

}
