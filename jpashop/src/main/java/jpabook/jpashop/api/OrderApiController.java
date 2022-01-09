package jpabook.jpashop.api;

import jpabook.jpashop.domain.*;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import jpabook.jpashop.service.query.OrderQueryService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * v1 엔티티 직접노출
     */
    @GetMapping("api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); // 강제 초기화해서 데이터를 뿌리게 하는 것
            order.getDelivery().getAddress();
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());
        }
        return all;
    }

    /**
     * v2 엔티티를 DTO로 변환
     * v2의 문제 DTO안에 ORDERITEMS 엔티티가 있음 DTO안에 엔티티가 있으면 안됨
     * -> 해결이 코드 주석 아래내용 orderitems도 dto로 변환해준다
     */
    @GetMapping("api/v2/orders")
    public Result ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return new Result(result);
    }

    /**
     * v3 페치조인 최적화
     * 1대다 페치조인 단점 페이징 불가능
     * setFirstResult(), setMaxResult()해서 돌려보면 날린 쿼리에 limit x.offset이 없음
     * 페이징쿼리와 1대다 페치조인하면 메모리에서 페이징처리하기때문에 성능의 문제
     * -> 뻥튀기된 데이터에서 페이징하면 원하는 결과가 나오지 않기 때문에 하이버네이트가 
     *    중복을 제거한 데이터로 메모리에서 페이징 처리 하도록 한다. 성능 문제 심각
     * 문제2. 컬렉션 페치 조인은 1개만 사용가능하다 컬렉션 둘 이상에 페치조인을 사용하면 데이터 조회에서
     *       데이터가 m*m 으로 뻥튀기되면서 row도 많아지며 데이터를 맞출 수 없는 부정합 문제발생
     */
    @GetMapping("api/v3/orders")
    public Result orderV3() {
        List<Order> orders = orderRepository.findAllWithItem();
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return new Result(result);
    }

    /**
     * spring.jpa.open-in-view 옵션 꺼져있을경우 해결하기 위해
     * query용 서비스를 따로 만드는 예제
     * OrderDto, orderItemDto도 쿼리 service 쪽에 옮겨야함 안 옮겼음
     */
//    private final OrderQueryService orderQueryService;
//    @GetMapping("api/v3/orders")
//    public Result orderV3() {
//        return orderQueryService.odersV3();
//    }

    /**
     * v3-1 페이징과 한계돌파
     * 컬렉션 엔티티를 조회하면서 페이징하는 방법
     * 1. xToOne관계는 모두 fetch join 한다.
     * 2. 컬렉션은 지연 로딩으로 조회한다.
     * 3. 지연 로딩 성능을 최적화하기 위해 hibernate.default_batch_fetch_size, @BatchSize를 적용한다.
     *    hibernate.default_batch_fetch_size : 글로벌 설정 .yml에 설정
     *    @BatchSize : 개별 최적화
     *    -> 상황에 따라 다르지만 글로벌 설정을 선호
     *    이 옵션을 사용하면 컬렉션이나, 프록시 객체를 한꺼번에 설정한 size 만큼 IN 쿼리로 조회한다.
     * -> 설정한 BatchSize만큼 미리 당겨온다 in쿼리를 통해
     * default_batch_fetch_size가 100이고 데이터가 1000개하면 in절을 100개씩 10번( for문이 10번 돈다)
     * -> pk 기반으로 in절이 나가기때문에 속도가 빠르다
     * default_batch_fetch_size는 in쿼리의 갯수를 말한다.
     * 아래의 경우 1(orders) + N(orderItems) + M(item)에서 fetch join과 batchsize를 이용해서 1 + 1 + 1 로 최적화
     * 참고: default_batch_fetch_size 의 크기는 적당한 사이즈를 골라야 하는데, 100~1000 사이를
     * 선택하는 것을 권장한다. 이 전략을 SQL IN 절을 사용하는데, 데이터베이스에 따라 IN 절 파라미터를
     * 1000으로 제한하기도 한다. 1000으로 잡으면 한번에 1000개를 DB에서 애플리케이션에 불러오므로 DB
     * 에 순간 부하가 증가할 수 있다. 하지만 애플리케이션은 100이든 1000이든 결국 전체 데이터를 로딩해야
     * 하므로 메모리 사용량이 같다. 1000으로 설정하는 것이 성능상 가장 좋지만, 결국 DB든 애플리케이션이든
     * 순간 부하를 어디까지 견딜 수 있는지로 결정하면 된다.
     */
    @GetMapping("api/v3.1/orders")
    public Result orderV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return new Result(result);
    }

    /**
     * v4 jpa에서 dto 직접조회
     * Query: 루트 1번, 컬렉션 N 번 실행
     * ToOne(N:1, 1:1) 관계들을 먼저 조회하고, ToMany(1:N) 관계는 각각 별도로 처리한다.
     * 이런 방식을 선택한 이유는 다음과 같다.
     * ToOne 관계는 조인해도 데이터 row 수가 증가하지 않는다.
     * ToMany(1:N) 관계는 조인하면 row 수가 증가한다.
     * row 수가 증가하지 않는 ToOne 관계는 조인으로 최적화 하기 쉬우므로 한번에 조회하고,
     * ToMany 관계는 최적화 하기 어려우므로 findOrderItems() 같은 별도의 메서드로 조회한다.
     */
    @GetMapping("api/v4/orders")
    public Result ordersV4() {
       return new Result(orderQueryRepository.findOrderQueryDtos());
    }

    /**
     * v5
     * Query: 루트 1번, 컬렉션 1번
     * ToOne 관계들을 먼저 조회하고, 여기서 얻은 식별자 orderId로 ToMany 관계인 OrderItem 을
     * 한꺼번에 조회
     * MAP을 사용해서 매칭 성능 향상(O(1))
     */
    @GetMapping("api/v5/orders")
    public Result ordersV5() {
        return new Result(orderQueryRepository.findAllByDto_optimization());
    }

    /**
     * v6
     * groupingBy 할때 OrderQueryDto에 @EqualsAndHashCode(of="orderId") 어떤것으로 묶을지 알려주는 것
     *
     * Query: 1번
     * 단점
     * 쿼리는 한번이지만 조인으로 인해 DB에서 애플리케이션에 전달하는 데이터에 중복 데이터가
     * 추가되므로 상황에 따라 V5 보다 더 느릴 수 도 있다.
     * 애플리케이션에서 추가 작업이 크다.
     * 페이징 불가능
     */
    @GetMapping("api/v6/orders")
    public Result ordersV6() {
        List<OrderFlatDto> flats = orderQueryRepository.findAllByDto_flat();
        List<OrderQueryDto> collect = flats.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(), o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(), o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(), e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(), e.getKey().getAddress(), e.getValue()))
                .collect(toList());


        return new Result(collect);
    }

    @Data
    @AllArgsConstructor
    static class Result<T>{
        private T data;
    }


    @Getter
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
//        private List<OrderItem> orderItems;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
//            order.getOrderItems().stream().forEach(o->o.getItem().getName()); // 초기화
//            orderItems = order.getOrderItems();
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(Collectors.toList());
        }
    }

    @Getter
    static class OrderItemDto {

        private String itemName; //상품명
        private int orderPrice; // 주문 가격
        private int count; // 주문 수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }


}
