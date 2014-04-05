package com.zegoggles.smssync.contacts;

public class ContactGroup {
    public final long _id;
    public final Type type;

    public enum Type {
        EVERYBODY,
        GROUP
    }

    public static final ContactGroup EVERYBODY = new ContactGroup(-1);

    public ContactGroup(final long id) {
        this._id = id;
        this.type = id > 0 ? Type.GROUP : Type.EVERYBODY;
    }

    public boolean isEveryBody() {
        return type == Type.EVERYBODY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactGroup that = (ContactGroup) o;
        if (_id != that._id) return false;
        if (type != that.type) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (_id ^ (_id >>> 32));
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override public String toString() {
        return "ContactGroup{" +
                "_id=" + _id +
                ", type=" + type +
                '}';
    }
}
