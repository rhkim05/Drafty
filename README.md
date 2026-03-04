# tablet-note-app

Basically Goodnotes + Parallel Pages

## 🛠 Tech Stack & Libraries (기술 스택 및 주요 라이브러리)

본 프로젝트는 UI의 개발 생산성과 드로잉 렌더링의 퍼포먼스를 모두 잡기 위해 **React Native와 Native(Kotlin)의 하이브리드 아키텍처**로 구현되었습니다.

### 1. Core & Native Engine (핵심 엔진 및 네이티브)

- **React Native**: 앱의 전체적인 뼈대와 UI 컴포넌트 구성. (렌더링 최적화를 위해 New Architecture 적용 권장)
- **Kotlin (Android Native)**: 캔버스 드로잉 엔진 및 터치 이벤트 처리 로직 구현.
- **Android Canvas API & Path**: JSI/Bridge 병목을 우회하여 지연 없는(Low-latency) 고성능 벡터 선 그리기 및 화면 렌더링.

### 2. UI & Interaction (프론트엔드 화면)

- **React Navigation**: 앱 내 화면 전환 (노트 목록 화면 ↔ 노트 편집 화면).
- **React Native Reanimated & Gesture Handler**: 툴바 확장, 색상 팔레트 팝업 등 네이티브 수준의 부드러운 60fps UI 애니메이션 처리.
- **Lucide React Native** (또는 `react-native-vector-icons`): 펜, 지우개, 실행 취소(Undo/Redo) 등 직관적인 벡터 아이콘 제공.

### 3. State Management (상태 관리)

- **Zustand**: 가볍고 빠른 전역 상태 관리. (현재 선택된 펜의 종류, 색상, 굵기, 지우개 모드 등의 툴바 상태를 RN과 Native 뷰 간에 동기화)

### 4. Storage & Data (데이터 저장)

- **React Native FS (File System)**: 사용자가 그린 수많은 좌표 데이터(JSON 방식) 및 썸네일 이미지를 기기 로컬 저장소에 빠르고 안전하게 읽기/쓰기.
