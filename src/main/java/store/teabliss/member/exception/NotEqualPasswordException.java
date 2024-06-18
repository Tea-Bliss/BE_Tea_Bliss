package store.teabliss.member.exception;

public class NotEqualPasswordException extends MemberException {

    public NotEqualPasswordException(String type) {
        super(type.equals("old") ? MemberStatus.NOT_EQUAL_OLD_PASSWORD : MemberStatus.NOT_EQUAL_NEW_PASSWORD);
    }

}
