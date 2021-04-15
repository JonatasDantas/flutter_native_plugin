import 'package:flutter/material.dart';
import 'package:flutter_native_code/pages/dashboard/dashboard.dart';
import 'package:flutter_native_code/pages/networking/networking.dart';

class TabsPage extends StatefulWidget {
  @override
  _TabsPageState createState() => _TabsPageState();
}

class _TabsPageState extends State<TabsPage> {
  int _selectedIndex = 0;

  static List<TabBarItem> _tabBarItens = [
    TabBarItem(
      navigationItem: BottomNavigationBarItem(
        icon: Icon(Icons.home_filled),
        label: "Home",
      ),
      widget: DashboardPage(),
    ),
    TabBarItem(
      navigationItem: BottomNavigationBarItem(
        icon: Icon(Icons.wifi),
        label: "Informações de rede",
      ),
      widget: NetworkingPage(),
    ),
  ];

  @override
  void initState() {
    super.initState();
  }

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          _tabBarItens.elementAt(_selectedIndex).navigationItem.label,
        ),
        centerTitle: true,
      ),
      body: _tabBarItens.elementAt(_selectedIndex).widget,
      bottomNavigationBar: BottomNavigationBar(
        items: _tabBarItens.map((tab) => tab.navigationItem).toList(),
        currentIndex: _selectedIndex,
        unselectedItemColor: Colors.grey[700],
        selectedItemColor: Colors.blue[400],
        onTap: _onItemTapped,
        showUnselectedLabels: true,
        type: BottomNavigationBarType.fixed,
      ),
    );
  }
}

class TabBarItem {
  final BottomNavigationBarItem navigationItem;
  final Widget widget;

  TabBarItem({this.navigationItem, this.widget});
}
