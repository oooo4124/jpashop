package jpabook.jpashop.service;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true) // 조회부분 트랜잭션 readOnly 해주면 jpa 성능 최적화
@RequiredArgsConstructor // final 있는 필드 가지고 생성자를 만들어줌 최종 방법
public class MemberService {

//    @Autowired
    private final MemberRepository memberRepository;

//    @Autowired // 세터 인젝션 이방식 장점은 테스트 코드 작성시 mock 사용가능 단점은 런타임에 누군가 변경할 위험
//    public void setMemberRepository(MemberRepository memberRepository) {
//        this.memberRepository = memberRepository;
//    }

    // 권장하는 의존성 주입 (생성자에 오토와이어드) / 생성자 하나인 경우 @Autowired 생략가능 스프링이 자동으로 해줌
//    @Autowired
//    public MemberService(MemberRepository memberRepository) {
//        this.memberRepository = memberRepository;
//    }

    /**
     * 회원 가입
     */
    @Transactional
    public Long join(Member member) {
        validateDuplicateMember(member); // 중복 회원 검증 이 방법도 동시에 똑같은 이름이 들어오면 문제 발생 따라서 이름에는 unique 제약조건 적용
        memberRepository.save(member);
        return member.getId();
    }

    private void validateDuplicateMember(Member member) {
        // exception
        List<Member> findMembers = memberRepository.findByName(member.getName());
        if (!findMembers.isEmpty()) {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        }
    }

    //회원 전체 조회
    public List<Member> findMember() {
        return memberRepository.findAll();
    }

    public Member findOne(Long memberId) {
        return memberRepository.findById(memberId).get();
    }

    @Transactional
    public void update(Long id, String name) {
        Member member = memberRepository.findById(id).get();
        member.setName(name);
    }
}
