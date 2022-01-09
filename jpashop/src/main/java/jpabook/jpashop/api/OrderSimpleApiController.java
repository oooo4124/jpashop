package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderSearch;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * xToOne(ManyToOne, OneToOne 최적화)
 * 연관관계
 * Order
 * Order -> Member
 * Order -> Delivery
 */

@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); //Lazy 강제 초기화
            order.getDelivery().getAddress(); //Lazy 강제 초기화
        }
        return all;
        // 첫번째 문제 -> order에 가보니 member가 있고 member에서 다시 order가 있고 무한루프에 빠짐
        // -> 해결 양방향에서 한쪽 JsonIgnore로 끊어줌
        // 두번째 문제 -> order를 가져옴 member에 가보면 fetch가 LAZY로 되어있음 지연로딩(db에서 안가져옴)이기때문에
        // NEW로 member객체 안가져오기때문에 하이버네이트에서 proxymember를 생성해서 넣어둠(bytebuddyinterceptor가 대신들어감)
        // 그래서 순수한 member객체가 아니라서 오류 -> 해결 : 하이버네이트 모듈 설치해서 강제 지연로딩 -> 문제 필요없는 것들 날림(쿼리)
        // 사용하지 않는 api 스펙 노출 성능 낭비등의 문제
    }

    // V2 엔티티를 DTO로 변환
    @GetMapping("/api/v2/simple-orders")
    public Result ordersV2() {
        //order 2개
        // 1(order 찾는 쿼리) + N(찾은 order 수) 문제 -> 1 + 회원 N(2) + 배송 N(2) = 쿼리가 최악의 경우 총 5번 실행 (N은 지연로딩 조회)
        // 같은 회원인 경우에는 영속성 컨텍스트에 있는 것을 사용해서 회원 N이 1이됨
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        //order가 2개라 2번 돈다
        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o)) // map은 A를 B로 바꾸는 것
                .collect(Collectors.toList());

        return new Result(result);

        /**
         * v2의 문제
         * stream으로 루프 돌릴때 getname과 getAddress에서 LAZY 초기화
         * 1 + N문제 쿼리가 너무 많이 실행(orders찾는 쿼리 1 + 루프 돌면서 지연로딩 N번(찾은 order의 수))
         * 주문이 10개라면 1 + 회원 지연로딩 10 + 배송 10 = 21번
         */
    }

    /**
     * v3 - 엔티티를 DTO로 변환 - 페치조인 활용
     * 페치조인을 통해 성능최적화 했음
     * 단점은 select에서 엔티티를 찍어 조회
     */
    @GetMapping("/api/v3/simple-orders")
    public Result ordersV3() {

        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());
        return new Result(result);
    }

    /**
     * v4엔티티를 불러오지 않고 JPA에서 DTO로 바로 조회
     * 
     * v4와 v3는 우열을 가리기 어려움 trade-off가있음
     * V3는 재사용성이 있음 엔티티로 조회하기 때문에 수정가능
     * v4는 select 쪽에서 좀 더 fit한 장점이 있으나 재사용성이 없음 해당 DTO를 사용할 때만 쓸 수 있음 하지만 성능은 좀 더 좋음 v3보다
     * v4는 dto로 조회하기때문에 변경 불가능, 코드가 더 지저분, repository가 api 스펙에 의존 api스펙이 바뀌면 뜯어 고쳐야함
     * v3 사용하는것이 더 좋을듯하다 대부분의 경우 성능차이도 크지 않음 네트워크가 좋아서,
     * 또한 대부분의 성능은 select 이후에 먹기때문에 이후 코드는 둘다 같아서 비슷
     * 고객이 실시간으로 엄청 많이 접근한다면 v4로 최적화하는 것에 대한 고민이 필요하다.
     */
    @GetMapping("/api/v4/simple-orders")
    public Result ordersV4() {
        List<OrderSimpleQueryDto> orderDtos = orderSimpleQueryRepository.findOrderDtos();
        return new Result(orderDtos);
    }

    @Data
    @AllArgsConstructor
    static class Result<T>{
        private T data;
    }


    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address; // 이런 밸류 오브젝트는 그냥 사용가능

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();

        }
    }
}
