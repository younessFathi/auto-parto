package auto.parto.api;

import auto.parto.dto.CustomerDto;
import auto.parto.service.iservice.ICustomerService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("customers")
@AllArgsConstructor
public class CustomerController {
    private final ICustomerService customerService;

    @PutMapping("/{id}/deactivate")
    public Mono<CustomerDto> deactivateSeller(@PathVariable String id) {
        return customerService.deactivateCustomer(id);
    }
}
