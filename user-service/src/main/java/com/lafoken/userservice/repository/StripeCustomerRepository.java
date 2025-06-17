package com.withfy.userservice.repository;

import com.withfy.userservice.entity.StripeCustomer;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public interface StripeCustomerRepository extends ReactiveCrudRepository<StripeCustomer, UUID> {

    @Query("INSERT INTO stripe_customers (id, stripe_customer_id, created_at, updated_at) " +
           "VALUES (:#{#stripeCustomer.id}, :#{#stripeCustomer.stripeCustomerId}, :#{#stripeCustomer.createdAt}, :#{#stripeCustomer.updatedAt})")
    Mono<Void> insertStripeCustomer(StripeCustomer stripeCustomer);
}

