package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusIamUserCommandRepository implements IamUserCommandRepository {

    private final IamUserMapper iamUserMapper;

    public MybatisPlusIamUserCommandRepository(IamUserMapper iamUserMapper) {
        this.iamUserMapper = iamUserMapper;
    }

    @Override
    public IamUser createUser(String userNo, String userName, String passwordHash, String email, String phone) {
        IamUserDO entity = new IamUserDO();
        entity.setUserNo(userNo);
        entity.setUserName(userName);
        entity.setPasswordHash(passwordHash);
        entity.setEmail(email);
        entity.setPhone(phone);
        entity.setStatus("ACTIVE");
        iamUserMapper.insert(entity);
        return new IamUser(
                entity.getId(),
                entity.getUserNo(),
                entity.getUserName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getStatus()
        );
    }

    @Override
    public boolean updateForImportByUserNo(String userNo, String userName, String passwordHash, String email, String phone) {
        return iamUserMapper.updateForImportByUserNo(userNo, userName, passwordHash, email, phone) > 0;
    }

    @Override
    public boolean updateStatus(Long userId, String status) {
        return iamUserMapper.updateStatus(userId, status) > 0;
    }
}