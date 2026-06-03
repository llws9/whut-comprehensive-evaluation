package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.domain.iam.model.IamRoleDetail;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MybatisPlusRoleAdminCommandRepository implements RoleAdminCommandRepository {

    private final IamRoleMapper iamRoleMapper;

    public MybatisPlusRoleAdminCommandRepository(IamRoleMapper iamRoleMapper) {
        this.iamRoleMapper = iamRoleMapper;
    }

    @Override
    public Optional<IamRoleDetail> findById(Long roleId) {
        IamRoleDO item = iamRoleMapper.selectById(roleId);
        return Optional.ofNullable(item).map(this::toDetail);
    }

    @Override
    public Optional<IamRoleDetail> findByRoleCode(String roleCode) {
        IamRoleDO item = iamRoleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("limit 1"));
        return Optional.ofNullable(item).map(this::toDetail);
    }

    @Override
    public IamRoleDetail create(String roleCode, String roleName, String roleScope, String status) {
        IamRoleDO item = new IamRoleDO();
        item.setRoleCode(roleCode);
        item.setRoleName(roleName);
        item.setRoleScope(roleScope);
        item.setStatus(status);
        item.setCreatedAt(LocalDateTime.now());
        iamRoleMapper.insert(item);
        return toDetail(item);
    }

    @Override
    public void update(Long roleId, String roleName, String roleScope, String status) {
        IamRoleDO item = new IamRoleDO();
        item.setId(roleId);
        item.setRoleName(roleName);
        item.setRoleScope(roleScope);
        item.setStatus(status);
        iamRoleMapper.updateById(item);
    }

    private IamRoleDetail toDetail(IamRoleDO item) {
        return new IamRoleDetail(
                item.getId(),
                item.getRoleCode(),
                item.getRoleName(),
                item.getRoleScope(),
                item.getStatus()
        );
    }
}
