package com.zegoggles.smssync.contacts;

public class ContactGroup {
    public final long _id;
    public final Type type;

    public enum Type {
        EVERYBODY,
        GROUP
    }

    public ContactGroup(final long id) {
        this._id = id;
        this.type = (id == ContactAccessor.EVERYBODY_ID ? Type.EVERYBODY : Type.GROUP);
    }
}
