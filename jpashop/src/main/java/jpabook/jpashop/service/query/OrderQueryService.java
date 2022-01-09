package jpabook.jpashop.service.query;

import jpabook.jpashop.api.OrderApiController;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

//    public Result orderV3() {
////        List<Order> orders = orderRepository.findAllWithItem();
////        List<OrderApiController.OrderDto> result = orders.stream()
////                .map(o -> new OrderApiController.OrderDto(o))
////                .collect(Collectors.toList());
////
////        return new OrderApiController.Result(result);
//    }

    @Data
    @AllArgsConstructor
    static class Result<T>{
        private T data;
    }
}
