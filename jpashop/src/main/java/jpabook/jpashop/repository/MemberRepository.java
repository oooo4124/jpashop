package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * spring data jpa 사용 JpaRepository<타입, pk 타입>
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    //jpa가 findByOOO 되어있으면 select from Member m where m.OOO = ? 이렇게 jpql을 만든다
    List<Member> findByName(String name);
}
