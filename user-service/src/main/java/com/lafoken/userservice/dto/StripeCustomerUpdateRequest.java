package com.withfy.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record StripeCustomerUpdateRequest(
    @NotBlank(message = "Stripe Customer ID cannot be blank")
    String stripeCustomerId
) {}
