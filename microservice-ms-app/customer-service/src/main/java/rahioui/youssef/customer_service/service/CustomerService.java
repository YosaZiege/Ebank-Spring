package rahioui.youssef.customer_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import rahioui.youssef.customer_service.entities.Customer;
import rahioui.youssef.customer_service.repository.CustomerRepository;

@Service
public class CustomerService {

    private CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not Found!"));
    }

    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

}
