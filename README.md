### Jetbrains Internship test 2017

Формат вывода дерева вызовов в файл человекочитаем, сериализация также поддерживается. Мотивация к 
формату файла такая: кажется, что запись такого лога в файл и последующее чтение глазами -- достаточно 
стандартное использование такого рода утилиты, поэтому чем файл удобнее для чтения, тем лучше. Каких-то 
очевидных улучшений, которые условно немного уменьшат читаемость и заметно упростят парсинг (вообще 
непонятно зачем, и так не слишком сложно), нет. В случаях, когда читаемость не нужна, можно просто 
использовать сериализацию.