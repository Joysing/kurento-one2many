//package com.sendroid.kurento.one2manycall.entity;
//
//import org.springframework.security.core.GrantedAuthority;
//
//import javax.persistence.*;
//
//@Entity
//@Table(name = "authorities")
//public class Authority implements GrantedAuthority {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//    private String authority;
//
//    public Authority() {
//    }
//
//    public Authority(String authority) {
//        this.authority = authority;
//    }
//
//    public Long getId() {
//        return id;
//    }
//
//    public void setId(Long id) {
//        this.id = id;
//    }
//
//    @Override
//    public String getAuthority() {
//        return authority;
//    }
//
//    @Override
//    public int hashCode() {
//        final int prime = 31;
//        int result = 1;
//        result = prime * result + ((authority == null) ? 0 : authority.hashCode());
//        return result;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj)
//            return true;
//        if (obj == null)
//            return false;
//        if (getClass() != obj.getClass())
//            return false;
//        Authority other = (Authority) obj;
//        if (authority == null) {
//            if (other.authority != null)
//                return false;
//        } else if (!authority.equals(other.authority))
//            return false;
//        return true;
//    }
//
//    @Override
//    public String toString() {
//        return "Authority [authority=" + authority + "]";
//    }
//}
