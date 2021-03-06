package com.hesong.weChatAdapter.account;

import java.util.List;

public interface AccountBo {

    void save(Account a);
    void update(Account a);
    void delete(Account a);
    @SuppressWarnings("rawtypes")
    List findByAcctype(String acctype);
    @SuppressWarnings("rawtypes")
    List getAllAccount();
    void saveAll(List<Account> list);
}
