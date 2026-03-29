class Person:
    '''
    this class is person
    '''
    name: str
    age: int
    print()
    def __init__(self):
        self.name ='df'
        self.age = 13
        
if __name__ == '__main__':
    print(Person().name)