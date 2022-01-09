package jpabook.jpashop.service;

import jpabook.jpashop.domain.item.Book;
import jpabook.jpashop.domain.item.Item;
import jpabook.jpashop.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional
    public void saveItem(Item item) {
        itemRepository.save(item);
    }

    /**
     * 준영속성 엔티티 변경감지 사용법
     */
    @Transactional
    public void updateItem(Long itemId, String name, int price, int stockQuantity) {
        Item findItem = itemRepository.findOne(itemId); //영속성 엔티티를 불러온다
        // 값을 넣어준다
//        findItem.change(id, name, price, stockQuantity); <-- 이런식으로 만들어라 setter 사용 자제
        findItem.setName(name);
        findItem.setPrice(price);
        findItem.setStockQuantity(stockQuantity);
        // set 사용하는것보다 change 메서드를 addstock처럼 만들어서 따로 관리해주는 것이 좋다
        
        // Transactional에의해 commit 된다
        // commit되면 jpa는 플러시를 날리고 변경된 것을 찾아 업데이트 쿼리를 날린다.
        
    }


    public List<Item> findItems() {
        return itemRepository.findAll();
    }

    public Item findOne(Long itemId) {
        return itemRepository.findOne(itemId);
    }
}
