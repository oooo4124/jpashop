package jpabook.jpashop.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jpabook.jpashop.domain.*;
import jpabook.jpashop.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final EntityManager em;

    public void save(Order order) {
        em.persist(order);
    }

    public Order findOne(Long id) {
        return em.find(Order.class, id);
    }

    public List<Order> findAll(OrderSearch orderSearch){

        /**
         * 값이 다 있다는 가정하에 코드 동적쿼리x
         */
//        return em.createQuery("select o from Order o join o.member m" +
//                " where o.status =:status" +
//                " and m.name like :name", Order.class)
//                .setParameter("status", orderSearch.getOrderStatus())
//                .setParameter("name", orderSearch.getMemberName())
////                .setFirstResult(100) // 100부터 시작해서 1000개 가져온다 (페이징)
//                .setMaxResults(1000) // 최대 1000개까지 조회
//                .getResultList();

        /**
         * query dsl을 사용한 동적쿼리 적용
         */
        JPAQueryFactory query = new JPAQueryFactory(em);
        QOrder order = QOrder.order;
        QMember member = QMember.member;

        // 이게 jpql로 바꿔서 실행이 된다.
        // 장점 컴파일시점에 오타가 잡힌다.
        return query
                .select(order)
                .from(order)
                .join(order.member, member)
                .where(statusEq(orderSearch.getOrderStatus()), nameLike(orderSearch.getMemberName())) //상태가 똑같으면 컨디션이 없을때 null로 반환되기 때문에 where에서 안써서 버림
                .limit(1000)
                .fetch();
    }

    //동적쿼리로 하려면 이렇게 메소드 만든다
    private BooleanExpression nameLike(String memberName) {
        if (!StringUtils.hasText(memberName)) {
            return null;
        }
        return QMember.member.name.like(memberName);
    }

    //condition 추가 동적쿼리용
    private BooleanExpression statusEq(OrderStatus statusCond) {
        if (statusCond == null) {
            return null;
        }
        return QOrder.order.status.eq(statusCond);
    }

    /**
     * 프로젝트 할 때 스프링 부트,
     * spring data jpa, 쿼리 dsl은 꼭 사용 실무에서 생산성을 극대화하며 코드도 깔끔하고 컴파일시점에 문법오류도 잡아줘서 개발을 깔끔하게 할 수 있다.
     */

    /**
     * JPA Criteria 로 처리 권장하는 방법 아님
     * 실무에서 사용하지 않음
     * 치명적인 단점 : 유지보수성이 0
     */
    public List<Order> findAllByCriteria(OrderSearch orderSearch) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> o = cq.from(Order.class);
        Join<Order, Member> m = o.join("member", JoinType.INNER); //회원과 조인
        List<Predicate> criteria = new ArrayList<>();
        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            Predicate status = cb.equal(o.get("status"),
                    orderSearch.getOrderStatus());
            criteria.add(status);
        }
        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            Predicate name =
                    cb.like(m.<String>get("name"), "%" +
                            orderSearch.getMemberName() + "%");
            criteria.add(name);
        }
        cq.where(cb.and(criteria.toArray(new Predicate[criteria.size()])));
        TypedQuery<Order> query = em.createQuery(cq).setMaxResults(1000); //최대1000건
        return query.getResultList();
    }

    /**
     * JPQL로 처리 실무에서 사용하지 않음
     * JPQL 쿼리를 문자로 생성하기는 번거롭고, 실수로 인한 버그가 충분히 발생할 수 있다.
     */
    public List<Order> findAllByString(OrderSearch orderSearch) {
        //language=JPAQL
        String jpql = "select o From Order o join o.member m";
        boolean isFirstCondition = true;
        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " o.status = :status";
        }
        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " m.name like :name";
        }
        TypedQuery<Order> query = em.createQuery(jpql, Order.class)
                .setMaxResults(1000); //최대 1000건
        if (orderSearch.getOrderStatus() != null) {
            query = query.setParameter("status", orderSearch.getOrderStatus());
        }
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            query = query.setParameter("name", orderSearch.getMemberName());
        }
        return query.getResultList();
    }



    /**
     * member와 delivery 를 order와 조인해서 select로 한번에 다 끌어옴
     * 이때는 LAZY 무시하고 진짜 객체에 값을 다 채워서 가져옴
     */
    public List<Order> findAllWithMemberDelivery() {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class
        ).getResultList();
    }

    /**
     *collection을 조인하는 예제
     * Order와 orderItem을 조인하면
     * order2개 orderItem 4개일때 
     * orderItem의 수만큼 Order가 뻥튀기
     * 문제는 데이터가 늘어나는 결과를 원하지 않았음
     * 해결법 distinct
     * 1. db에 distinct 날려준다
     * 2. 엔티티가 중복인경우 중복을 제거 후 컬렉션에 담아준다.
     * JPQL의 DISTINCT는 SQL에 DISTINCT를 추가하는 것은 물론이고, 어플리케이션에서 한번 더 중복을 제거한다.
     * 이 특징을 이용해서 위의 컬렉션 패치 조인에서 리스트가 중복되서 나오는 문제를 해결할 수 있다.
     * 데이터베이스의 distinct는 데이터가 전부 똑같아야 중복제거 되지만 jpa는 order를 가져올때 같은 id값이면 하나를 버리고 반환
     */
    
    public List<Order> findAllWithItem() {
        return em.createQuery(
                        "select distinct o from Order o" +
                                " join fetch o.member m" +
                                " join fetch o.delivery d" +
                                " join fetch o.orderItems oi" +
                                " join fetch oi.item i", Order.class)
                .getResultList();
    }

    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                        "select o from Order o" +
                                " join fetch o.member m" +
                                " join fetch o.delivery d", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();

    }

    /**
     * order가 OrderSimpleQueryDto에 매핑될 수 없음
     * 이때는 엔티티나 밸류 오브젝트(embedable-> Address같은)만 jpa는 기본적으로 반환할 수 있다.
     * dto같은 것은 안됨 하려면 new operate를 사용(엔티티 넘기는 것이 안되기 때문에 아래 같이 작성 adress는 밸류타입이라 가능)
     */
//    public List<OrderSimpleQueryDto> findOrderDtos() {
//        return em.createQuery(
//                "select new jpabook.jpashop.repository.OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)"+
//                        " from Order o" +
//                        " join o.member m" +
//                        " join o.delivery d", OrderSimpleQueryDto.class)
//                .getResultList();
//    }
}
