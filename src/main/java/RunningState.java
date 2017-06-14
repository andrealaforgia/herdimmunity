import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static java.lang.Math.*;

public class RunningState extends State {

    private static final int VACCINATED_PERCENTAGE = 80;

    Collection<Cell> cells = new ArrayList<>();

    private long startTime;
    private PopulationStats populationStats;

    static class PopulationStats {
        int max = -1;
        int min = -1;
        int illMax = -1;
        int illMin = -1;
        List<Integer> counts = new ArrayList<>();
        List<Integer> illCounts = new ArrayList<>();

        void updateIllCount(int illCount) {
            if (!illCounts.isEmpty() && illCounts.get(illCounts.size() - 1).equals(illCount)) return;

            if (illMin == -1) illMin = illCount;
            else illMin = Math.min(illMin, illCount);

            if (illMax == -1) illMax = illCount;
            else illMax = Math.max(illMax, illCount);

            this.illCounts.add(illCount);
        }

        interface Exporter {
            void addCounts(List<Integer> counts);

            void addIllCounts(List<Integer> counts);

            void addLimits(int min, int max, int illMin, int illMax);

            void export();
        }

        void export(Exporter exporter) {
            exporter.addLimits(min, max, illMin, illMax);
            exporter.addCounts(Collections.unmodifiableList(counts));
            exporter.addIllCounts(Collections.unmodifiableList(illCounts));
            exporter.export();
        }

        void updatePopulationCount(int count) {
            if (!counts.isEmpty() && counts.get(counts.size() - 1).equals(count)) return;

            if (min == -1) min = count;
            else min = Math.min(min, count);

            if (max == -1) max = count;
            else max = Math.max(max, count);

            counts.add(count);
        }
    }

    static class Age {
        int years;
        int deathYears;

        public Age(int years, int deathYears) {
            this.years = years;
            this.deathYears = deathYears;
        }

        void grow(int years) {
            this.years += years;
            if (this.years > deathYears) {
                this.years = deathYears;
            }
        }

        void reduceDeathAge(int years) {
            deathYears -= years;
            if (deathYears < this.years) {
                deathYears = this.years;
            }
        }

        boolean isTimeToDie() {
            return years == deathYears;
        }
    }

    enum Health {
        SOUND,
        ILL
    }

    enum Vaccination {
        //NON_VACCINABLE,
        NOT_VACCINATED,
        VACCINATED
    }

    static class CellState {
        double x, y;
        Age age;
        Health health;
        Vaccination vaccination;

        public CellState(double x, double y, Age age, Health health, Vaccination vaccination) {
            this.x = x;
            this.y = y;
            this.age = age;
            this.health = health;
            this.vaccination = vaccination;
        }

        public double getRadius() {
            return Math.min(age.years / 3 + 2, 8);
        }

        public Color getColor() {
            if (health == Health.ILL) {
                return Color.RED;
            }
            if (vaccination == Vaccination.NOT_VACCINATED) {
                return Color.YELLOW;
            }
            if (vaccination == Vaccination.VACCINATED) {
                return Color.BLUE;
            }
            return Color.GRAY;
        }

        public boolean isWithinHorizontalLimits(int minX, int maxX) {
            double radius = getRadius();
            return (this.x - radius) > minX && (this.x + radius) < maxX;
        }

        public boolean isWithinVerticalLimits(int minY, int maxY) {
            double radius = getRadius();
            return (this.y - radius) > minY && (this.y + radius) < maxY;
        }
    }

    static class Cell {
        CellState state;
        double dx;
        double dy;
        long lastGrowthTimestamp;
        long minTimeBetweenGrowths;
        long childrenToDeliver;

        Cell(CellState initialState,
             double dx,
             double dy,
             long birthTime,
             long minTimeBetweenGrowths,
             int childrenToDeliver) {
            this.state = initialState;
            this.dx = dx;
            this.dy = dy;
            this.lastGrowthTimestamp = birthTime;
            this.minTimeBetweenGrowths = minTimeBetweenGrowths;
            this.childrenToDeliver = childrenToDeliver;
        }

        static Cell makeNew(double x, double y, Health health) {
            Random random = new Random();

            CellState cellState = new CellState(
                    x, y,
                    new Age(0, random.nextInt(50) + 50),
                    health,
                    random.nextInt(100) < VACCINATED_PERCENTAGE ? Vaccination.VACCINATED : Vaccination.NOT_VACCINATED);

            return new Cell(cellState,
                    (random.nextDouble() * 3 / (random.nextDouble() * 5 + 1))
                            * (random.nextInt(100) < 50 ? 1 : -1),
                    (random.nextDouble() * 3 / (random.nextDouble() * 5 + 1))
                            * (random.nextInt(100) < 50 ? 1 : -1),
                    System.currentTimeMillis(),
                    500,
                    random.nextInt(3));
        }

        void animate(long time) {
            advance();
            growIfPossible(time);
        }

        void animate() {
            animate(System.currentTimeMillis());
        }

        void tryToInfect() {
            if (state.health == Health.ILL) return;
            if (state.vaccination == Vaccination.VACCINATED) return;
//            if (state.vaccination == Vaccination.NON_VACCINABLE ||
//                    state.vaccination == Vaccination.NOT_VACCINATED) {
            if (state.vaccination == Vaccination.NOT_VACCINATED) {
                state.health = Health.ILL;
                state.age.reduceDeathAge(state.age.years / 2);
            }
        }

        void invertHorizontalDirection() {
            dx = -dx;
        }

        void invertVerticalDirection() {
            dy = -dy;
        }

        boolean isFertile() {
            return state.health != Health.ILL &&
                    childrenToDeliver > 0
                    && state.age.years >= 18 && state.age.years <= 50;
        }

        boolean isCloseTo(Cell cell) {
            double distX = cell.state.x - state.x;
            double distY = cell.state.y - state.y;
            return sqrt(distX * distX + distY * distY) < 10.0;
        }

        CellState getState() {
            return state;
        }

        private CellState advance() {
            state.x += dx;
            state.y += dy;
            return state;
        }

        private CellState growIfPossible(long time) {
            if (time - lastGrowthTimestamp >= minTimeBetweenGrowths) {
                lastGrowthTimestamp = time;
                state.age.grow(1);
            }
            return state;
        }

        public boolean isWithinVerticalLimits(int minY, int maxY) {
            return state.isWithinVerticalLimits(minY, maxY);
        }

        public boolean isWithinHorizontalLimits(int minX, int maxX) {
            return state.isWithinHorizontalLimits(minX, maxX);
        }

        public boolean isDead() {
            return state.age.isTimeToDie();
        }

        public Cell deliverChild() {
            --childrenToDeliver;
            return makeNew(state.x, state.y, Health.SOUND);
        }

        public boolean isIll() {
            return state.health == Health.ILL;
        }
    }

    public RunningState(AppState appState) throws IOException {
        this.appState = appState;
        init();
    }

    public void init() throws IOException {

        populationStats = new PopulationStats();

        Random random = new Random();
        for (int i = 0; i < 500; i++) {
            int cellRadius = 8;
            Cell cell = Cell.makeNew(
                    random.nextInt(1500 - cellRadius - 1),
                    random.nextInt(700 - cellRadius - 1),
                    Health.SOUND
            );
            cells.add(cell);
        }
        startTime = System.currentTimeMillis();
    }

    public void update() throws IOException {
        Collection<Cell> deadCells = new ArrayList<>();
        Collection<Cell> bornCells = new ArrayList<>();
        Collection<Cell> toBeInfected = new ArrayList<>();
        for (Cell cell : cells) {
            cell.animate();

            for (Cell otherCell : cells) {
                if (otherCell == cell) continue;
                if (!otherCell.isIll()) continue;
                if (otherCell.isCloseTo(cell)) {
                    toBeInfected.add(cell);
                }
            }

            if (cell.isDead()) {
                deadCells.add(cell);
            } else {
                if (!cell.isWithinHorizontalLimits(0, 1500)) {
                    cell.invertHorizontalDirection();
                }
                if (!cell.isWithinVerticalLimits(0, 700)) {
                    cell.invertVerticalDirection();
                }
                if (cell.isFertile()) {
                    bornCells.add(cell.deliverChild());
                }
            }
        }

        for (Cell cell : toBeInfected) {
            cell.tryToInfect();
        }

        cells.removeAll(deadCells);
        cells.addAll(bornCells);

        if (System.currentTimeMillis() - startTime > 10_000) {
            cells.add(Cell.makeNew(1500 / 2, 700 / 2, Health.ILL));
            startTime = System.currentTimeMillis();
        }

        populationStats.updatePopulationCount(cells.size());

        int illCount = 0;
        for (Cell cell : cells) {
            if (cell.isIll()) {
                ++illCount;
            }
        }

        if (illCount > 0) {
            populationStats.updateIllCount(illCount);
        }
    }

    @Override
    public void draw(final Graphics2D g) {
        g.clearRect(0, 0, 1500, 700);
        for (Cell cell : cells) {
            CellState cellState = cell.getState();
            double radius = cellState.getRadius();
            Shape drawnCell = new Ellipse2D.Double(cellState.x, cellState.y, radius, radius);
            g.setColor(cellState.getColor());
            g.draw(drawnCell);
            g.fill(drawnCell);
        }

        PopulationStats.Exporter populationCountUIRenderer = new PopulationStats.Exporter() {
            List<Integer> counts = Collections.emptyList();
            List<Integer> illCounts = Collections.emptyList();
            int min, max;
            int illMin, illMax;
            long lastUpdateTimeStamp = 0;

            @Override
            public void addCounts(List<Integer> counts) {
                this.counts = counts;
            }

            @Override
            public void addIllCounts(List<Integer> counts) {
                this.illCounts = counts;
            }

            @Override
            public void addLimits(int min, int max, int illMin, int illMax) {
                this.min = min;
                this.max = max;
                this.illMin = illMin;
                this.illMax = illMax;
            }

            @Override
            public void export() {
                if (System.currentTimeMillis() - lastUpdateTimeStamp < 1_000) return;
                lastUpdateTimeStamp = System.currentTimeMillis();

                g.setColor(Color.DARK_GRAY);
                g.drawLine(0, 50, 1500, 50);
                g.drawLine(0, 650, 1500, 650);

                drawCounts(counts, min, max, Color.GREEN);
                drawCounts(illCounts, illMin, illMax, Color.RED);
            }

            private void drawCounts(List<Integer> counts, int min, int max, Color color) {
                if (max-min == 0) return;
                int x = 1499 - counts.size();
                int prevY = -1;
                int prevX = -1;
                for (Integer count : counts) {
                    int y = 645 - ((645 - 55) * (count - min)) / (max - min);
                    if (prevY == -1) {
                        prevY = y;
                        prevX = x;
                        continue;
                    }
                    g.setColor(color);
                    g.drawLine(prevX, prevY, x, y);
                    //g.drawString(""+count, x, y);
                    prevX = x;
                    prevY = y;
                    ++x;
                }
            }
        };

        populationStats.export(populationCountUIRenderer);
    }

    public void keyPressed(int k) {
    }

    public void keyReleased(int k) {
    }
}

