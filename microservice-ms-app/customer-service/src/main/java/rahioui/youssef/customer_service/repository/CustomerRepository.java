package rahioui.youssef.customer_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import rahioui.youssef.customer_service.entities.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

}
