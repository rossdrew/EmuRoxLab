Is there any benefit to seperating addressing?

```java
interface AddressBus{}
class MyAddressBus {
    void setAddress(int address);
    int getAddress();
}
interface DataBus{
    int fetch();
}
class myDataBus implements AddressedDataBus{
    AddressedDataBus(AddressBus addressBus){}

    public int fetch(){
        return memory[addressBus.getAddress()];
    }
}

AddressBus addressBus = new MyAddressBus();
DataBus dataBus = new MyDataBus(addressBus);
//read
addressBuss.setAddress(10);
int value = dataBus.fetch()
```