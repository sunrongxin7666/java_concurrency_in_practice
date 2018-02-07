public class Animal{
    private String name;

    public Animal(String name){
        setName(name);
    }

    public void setName(String name){
        this.name = name;
    }
    public String getName(){
        return this.name;
    }

    @Override
    public boolean equals(Object other){
        if(other == this) return true;
        if(other == null) return false;
        if(this.getClass()!=other.getClass()) return false;

        return this.name.equals(((Animal)other).getName());
    }

    @Override
    public int hashCode(){
        return this.name.hashCode();
    }
}
